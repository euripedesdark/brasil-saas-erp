#!/usr/bin/env python3
"""Install staging dependencies in private user directories, never production."""
import json
import os
import pathlib
import secrets
import shutil
import socket
import subprocess
import time
import urllib.request
import urllib.error

root = pathlib.Path.home() / '.local/share/brasil-saas-staging'
units = pathlib.Path.home() / '.config/systemd/user'
envfile = root / 'staging.env'
settings = dict(line.split('=', 1) for line in envfile.read_text().splitlines() if line and not line.startswith('#'))
os.environ['XDG_RUNTIME_DIR'] = f'/run/user/{os.getuid()}'
os.environ['DBUS_SESSION_BUS_ADDRESS'] = f'unix:path=/run/user/{os.getuid()}/bus'

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def private(path, content):
    path.write_text(content)
    path.chmod(0o600)

def service(name, command, memory, extra=''):
    private(units / (name + '.service'), '[Unit]\nDescription=Isolated ERP staging ' + name + '\n[Service]\nType=simple\nSlice=brasil-saas-staging.slice\nWorkingDirectory=' + str(root) + '\nEnvironmentFile=' + str(envfile) + '\nExecStart=' + command + '\nRestart=on-failure\nRestartSec=10\nMemoryMax=' + memory + '\nNice=15\n' + extra + '[Install]\nWantedBy=default.target\n')

def wait_port(port, limit=90):
    deadline = time.monotonic() + limit
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=1):
                return
        except OSError:
            time.sleep(2)
    raise RuntimeError(f'Staging dependency did not listen on {port}')

def check_login():
    with urllib.request.urlopen('http://127.0.0.1:18081/login', timeout=5) as response:
        assert response.status == 200
        assert '<html' in response.read().decode().lower()

required = {name: shutil.which(name) for name in ['mongod', 'mongosh', 'redis-server', 'rabbitmq-server', 'minio', 'java']}
if not all(required.values()):
    raise RuntimeError('Missing runtime tools: ' + ', '.join(k for k, v in required.items() if not v))
available = int(next(line.split()[1] for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')))
if available < 1400 * 1024:
    raise RuntimeError('Not enough available memory to start isolated staging safely (need 1400 MiB)')

for component in ['mongo', 'redis', 'rabbitmq', 'minio']:
    folder = root / component
    folder.mkdir(exist_ok=True)
    folder.chmod(0o700)
for key in ['MONGO_PASSWORD', 'STAGING_ADMIN_PASSWORD']:
    settings.setdefault(key, secrets.token_urlsafe(36))
settings['MONGODB_URI'] = 'mongodb://stagingadmin:' + settings['MONGO_PASSWORD'] + '@127.0.0.1:27018/brasil_saas_staging?authSource=admin'
settings.update({
    'DB_USER': 'sa',
    'SPRING_DATASOURCE_URL': settings['DB_URL'] + '?currentSchema=brasil_saas',
    'SPRING_DATASOURCE_USERNAME': 'sa',
    'SPRING_DATASOURCE_PASSWORD': settings['DB_PASSWORD'],
    'SPRING_FLYWAY_URL': settings['DB_URL'] + '?currentSchema=brasil_saas',
    'SPRING_FLYWAY_USER': 'sa',
    'SPRING_FLYWAY_PASSWORD': settings['DB_PASSWORD'],
    'SPRING_DATA_MONGODB_URI': settings['MONGODB_URI'],
    'SPRING_MAIL_HOST': '127.0.0.1',
    'SPRING_MAIL_PORT': '19025',
    'SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH': 'false',
    'SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE': 'false',
    'MINIO_ROOT_USER': settings['MINIO_ACCESS_KEY'],
    'MINIO_ROOT_PASSWORD': settings['MINIO_SECRET_KEY'],
    'RABBITMQ_NODENAME': 'erp_staging@localhost',
    'RABBITMQ_NODE_IP_ADDRESS': '127.0.0.1',
    'RABBITMQ_NODE_PORT': '15673',
    'RABBITMQ_DIST_PORT': '35673',
    'ERL_EPMD_PORT': '14369',
    'RABBITMQ_MNESIA_BASE': str(root / 'rabbitmq/data'),
    'RABBITMQ_LOG_BASE': str(root / 'rabbitmq/log'),
    'RABBITMQ_PID_FILE': str(root / 'rabbitmq/rabbit.pid'),
    'RABBITMQ_CONFIG_FILE': str(root / 'rabbitmq/rabbitmq.conf'),
    'RABBITMQ_CONF_ENV_FILE': str(root / 'rabbitmq/rabbitmq-env.conf'),
    'RABBITMQ_ENABLED_PLUGINS_FILE': str(root / 'rabbitmq/enabled_plugins'),
    'RABBITMQ_PLUGINS_EXPAND_DIR': str(root / 'rabbitmq/plugins'),
    'RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS': '"+S 2:2 +A 4"',
    'BRASIL_SAAS_BOOTSTRAP_ADMIN_ENABLED': 'true',
    'BRASIL_SAAS_BOOTSTRAP_ADMIN_USERNAME': 'staging-admin',
    'BRASIL_SAAS_BOOTSTRAP_ADMIN_PASSWORD': settings['STAGING_ADMIN_PASSWORD'],
    'BRASIL_SAAS_BOOTSTRAP_ADMIN_EMAIL': 'staging-admin@example.invalid',
    'LOGGING_CONFIG': str(root / 'logback-staging.xml'),
    'SPRING_DATA_MONGODB_AUTO_INDEX_CREATION': 'false',
    'SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP': 'true',
    'SPRING_RABBITMQ_LISTENER_DIRECT_AUTO_STARTUP': 'true',
})
private(root / 'logback-staging.xml', '<configuration><appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender"><encoder><pattern>%d %-5level %logger{36} - %msg%n</pattern></encoder></appender><root level="INFO"><appender-ref ref="CONSOLE"/></root></configuration>\n')
private(root / 'smtp-sink.py', '''import socketserver
class Sink(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(30)
        def reply(s):
            self.wfile.write((s+'\\r\\n').encode()); self.wfile.flush()
        reply('220 staging.local SMTP sink')
        while True:
            line=self.rfile.readline(65536)
            if not line: break
            cmd=line.split(b' ',1)[0].strip().upper()
            if cmd in (b'EHLO',b'HELO'): reply('250 staging.local')
            elif cmd==b'DATA':
                reply('354 End with dot')
                while True:
                    data=self.rfile.readline(65536)
                    if not data or data in (b'.\\r\\n',b'.\\n'): break
                reply('250 Message discarded by staging sink')
            elif cmd==b'QUIT': reply('221 Bye'); break
            elif cmd in (b'MAIL',b'RCPT',b'RSET',b'NOOP'): reply('250 OK')
            else: reply('502 Unsupported command')
class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True
Server(('127.0.0.1',19025),Sink).serve_forever()
''')
private(envfile, ''.join(k + '=' + v + '\n' for k, v in settings.items()))
private(root / 'staging-access.txt', 'Username: staging-admin\nPassword: ' + settings['STAGING_ADMIN_PASSWORD'] + '\n')
private(root / 'redis/redis.conf', 'bind 127.0.0.1\nport 16379\nprotected-mode yes\nrequirepass ' + settings['REDIS_PASSWORD'] + '\ndir ' + str(root / 'redis') + '\nmaxmemory 16mb\nmaxmemory-policy allkeys-lru\nappendonly yes\ndaemonize no\n')
private(root / 'rabbitmq/rabbitmq-env.conf', '')
private(root / 'rabbitmq/enabled_plugins', '[].\n')
private(root / 'rabbitmq/rabbitmq.conf', 'listeners.tcp.1 = 127.0.0.1:15673\ndefault_user = staging\ndefault_pass = ' + settings['RABBIT_PASSWORD'] + '\ndefault_vhost = /staging\nvm_memory_high_watermark.absolute = 64MiB\ndisk_free_limit.absolute = 100MB\nlog.console = true\n')
private(units / 'brasil-saas-staging.slice', '[Unit]\nDescription=Bounded isolated ERP staging resources\n[Slice]\nMemoryHigh=1280M\nMemoryMax=1536M\nCPUQuota=50%\n')
pgunit = units / 'brasil-saas-staging-postgres.service'
content = pgunit.read_text()
if 'Slice=' not in content:
    private(pgunit, content.replace('[Service]\n', '[Service]\nSlice=brasil-saas-staging.slice\n'))
service('brasil-saas-staging-mongo', required['mongod'] + ' --bind_ip 127.0.0.1 --port 27018 --dbpath ' + str(root / 'mongo') + ' --wiredTigerCacheSizeGB 0.25 --auth', '384M')
service('brasil-saas-staging-redis', required['redis-server'] + ' ' + str(root / 'redis/redis.conf'), '96M')
service('brasil-saas-staging-rabbit', required['rabbitmq-server'], '256M')
service('brasil-saas-staging-minio', required['minio'] + ' server --address 127.0.0.1:19000 --console-address 127.0.0.1:19001 ' + str(root / 'minio'), '256M')
service('brasil-saas-staging-app', required['java'] + ' -Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:MaxDirectMemorySize=64m -XX:ActiveProcessorCount=2 -jar ' + str(root / 'app.jar'), '768M', 'TimeoutStartSec=300\n')
service('brasil-saas-staging-mail', shutil.which('python3') + ' ' + str(root / 'smtp-sink.py'), '32M')
names = ['brasil-saas-staging-' + component + '.service' for component in ['postgres', 'mongo', 'redis', 'rabbit', 'minio', 'app']]
names.append('brasil-saas-staging-mail.service')
run(['systemctl', '--user', 'daemon-reload'])
try:
    run(['systemctl', '--user', 'start', names[0]])
    wait_port(15432, 30)
    pgenv = os.environ.copy()
    pgenv['PGPASSWORD'] = (root / 'postgres/password').read_text().strip()
    # V187 targets the operational role sa. Initialize ownership before Flyway
    # takes its schema-history lock, using only this private PostgreSQL cluster.
    owner_sql = "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sa') THEN CREATE ROLE sa NOLOGIN; END IF; END $$;\n"
    owner_sql += "ALTER ROLE sa LOGIN SUPERUSER PASSWORD '" + settings['DB_PASSWORD'] + "';\n"
    owner_sql += """DO $$ DECLARE r record; BEGIN
      FOR r IN SELECT c.oid::regclass AS rel FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname IN ('brasil_saas','brasil_saas_dl') AND c.relkind='r' AND pg_get_userbyid(c.relowner) <> 'sa'
      LOOP EXECUTE format('ALTER TABLE %s OWNER TO sa', r.rel); END LOOP;
      FOR r IN SELECT c.oid::regclass AS rel FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname IN ('brasil_saas','brasil_saas_dl') AND c.relkind='S' AND pg_get_userbyid(c.relowner) <> 'sa'
      LOOP EXECUTE format('ALTER SEQUENCE %s OWNER TO sa', r.rel); END LOOP;
    END $$;
    """
    # Supply a synthetic tenant only in the isolated, already migrated database.
    # DataBootstrap otherwise explicitly inserts NULL into regime_tributario.
    owner_sql += """DO $$ BEGIN
      IF to_regclass('brasil_saas.bc_core_empresa') IS NOT NULL THEN
        INSERT INTO brasil_saas.bc_core_empresa
          (razao_social,nome_fantasia,cnpj,uf,status,regime_tributario)
        SELECT 'EMPRESA FICTICIA DE STAGING','ERP Staging','00000000000000','RS','ATIVO','SIMPLES_NACIONAL'
        WHERE NOT EXISTS (SELECT 1 FROM brasil_saas.bc_core_empresa);
      END IF;
    END $$;
    """
    run(['psql', '-X', '-h', '127.0.0.1', '-p', '15432', '-U', 'staging_owner', '-d', 'brasil_saas_staging', '-v', 'ON_ERROR_STOP=1'], input=owner_sql, text=True, env=pgenv, stdout=subprocess.DEVNULL)
    run(['systemctl', '--user', 'start', names[1]])
    wait_port(27018)
    mongoenv = os.environ.copy()
    mongoenv['STAGING_MONGO_PASSWORD'] = settings['MONGO_PASSWORD']
    ping = subprocess.run([required['mongosh'], settings['MONGODB_URI'], '--quiet', '--eval', 'db.runCommand({ping:1})'], env=mongoenv, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if ping.returncode:
        script = "db.getSiblingDB('admin').createUser({user:'stagingadmin',pwd:process.env.STAGING_MONGO_PASSWORD,roles:[{role:'root',db:'admin'}]})"
        run([required['mongosh'], '--host', '127.0.0.1', '--port', '27018', '--quiet', '--eval', script], env=mongoenv, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for name, port in zip(names[2:5], [16379, 15673, 19000]):
        run(['systemctl', '--user', 'start', name])
        wait_port(port)
    run(['systemctl', '--user', 'start', names[6]])
    wait_port(19025)
    run(['systemctl', '--user', 'start', names[5]])
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            check_login()
            with urllib.request.urlopen('http://127.0.0.1:18081/actuator/health', timeout=5) as response:
                health = json.load(response)
                assert health['status'] == 'UP', health
            break
        except (OSError, AssertionError):
            time.sleep(3)
    else:
        raise RuntimeError('Staging application did not become healthy')
    payload = json.dumps({'username': 'staging-admin', 'password': settings['STAGING_ADMIN_PASSWORD']}).encode()
    request = urllib.request.Request('http://127.0.0.1:18081/api/auth/login', data=payload, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=10) as response:
        login = json.load(response)
        assert login.get('data', {}).get('accessToken'), 'No access token returned'
    for name in names:
        run(['systemctl', '--user', 'enable', name], stdout=subprocess.DEVNULL)
    print('STAGING_HEALTH_UP; LOGIN_AND_AUTH_OK; ISOLATED_DEPENDENCIES_OK')
except Exception:
    run(['systemctl', '--user', 'stop', *names], stdout=subprocess.DEVNULL)
    print('Staging services stopped after failed validation; production not changed')
    raise
