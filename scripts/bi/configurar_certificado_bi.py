#!/usr/bin/env python3
"""Emite somente a identidade BI na CA existente; nao cria bancos, roles ou CA."""
import argparse
import fcntl
import os
from pathlib import Path
import pwd
import re
import shutil
import subprocess
import tempfile
import time

ROLE = 'brasil_saas_bi_reader'
BEGIN = '# BEGIN BRASIL SAAS BI CERT'
END = '# END BRASIL SAAS BI CERT'


def clean_env():
    return {k: v for k, v in os.environ.items() if not k.startswith('PG') and k != 'JAVA_TOOL_OPTIONS' and k != '_JAVA_OPTIONS' and k != 'JDK_JAVA_OPTIONS'}


def run(argv, *, env=None, binary=False):
    return subprocess.run([str(x) for x in argv], check=True, capture_output=True,
                          text=not binary, env=env or clean_env(), timeout=120).stdout


def fingerprint(path):
    return run(['openssl', 'x509', '-in', path, '-noout', '-fingerprint', '-sha256']).strip()


def managed_hba(original):
    pattern = re.compile(re.escape(BEGIN) + r'\n.*?' + re.escape(END) + r'\n?', re.S)
    if original.count(BEGIN) != original.count(END) or original.count(BEGIN) > 1:
        raise RuntimeError('Bloco HBA BI incompleto ou duplicado; revisar manualmente')
    preserved = pattern.sub('', original)
    rules = [BEGIN]
    for address in ('127.0.0.1/32', '::1/128'):
        rules += [f'hostssl brasil_saas_bi {ROLE} {address} cert clientcert=verify-full',
                  f'host brasil_saas_bi {ROLE} {address} reject']
    return '\n'.join(rules + [END, '']) + preserved


def replace_file(path, contents, mode, uid, gid):
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as out:
        tmp = Path(out.name)
        try:
            out.write(contents); out.flush(); os.fsync(out.fileno())
            os.chmod(tmp, mode); os.chown(tmp, uid, gid)
            # Preserva o contexto SELinux de arquivos existentes (especialmente HBA).
            if path.exists() and 'security.selinux' in os.listxattr(path):
                os.setxattr(tmp, 'security.selinux', os.getxattr(path, 'security.selinux'))
            os.replace(tmp, path)
        finally:
            tmp.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pki', required=True, type=Path, help='PKI Easy-RSA original com ca.crt e private/ca.key')
    parser.add_argument('--easyrsa', required=True, type=Path)
    parser.add_argument('--app-user', required=True, help='Usuario Unix efetivo do ERP')
    parser.add_argument('--jdbc-jar', required=True, type=Path, help='Driver PostgreSQL usado pelo ERP')
    parser.add_argument('--cert-dir', type=Path, default=Path('/etc/brasil-saas/pki'))
    parser.add_argument('--pg-port', type=int, default=5432)
    parser.add_argument('--pg-socket', default='/var/run/postgresql')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('Execute com sudo; altera somente certificado BI e bloco HBA BI')
    account = pwd.getpwnam(args.app_user)
    for command in ('openssl', 'psql', 'runuser', 'java'):
        if not shutil.which(command): raise RuntimeError(f'Dependencia ausente: {command}')
    args.pki = args.pki.resolve(); args.cert_dir = args.cert_dir.resolve()
    args.easyrsa = args.easyrsa.resolve(); args.jdbc_jar = args.jdbc_jar.resolve()
    for path in (args.pki / 'ca.crt', args.pki / 'private/ca.key', args.easyrsa,
                 args.cert_dir / 'ca.crt', args.jdbc_jar):
        if not path.is_file(): raise RuntimeError(f'Arquivo existente obrigatorio ausente: {path}')
    if not os.access(args.easyrsa, os.X_OK): raise RuntimeError('Easy-RSA nao executavel')
    if fingerprint(args.pki / 'ca.crt') != fingerprint(args.cert_dir / 'ca.crt'):
        raise RuntimeError('CA Easy-RSA difere da CA instalada. Nenhuma CA sera substituida')
    # Serializa emissao e HBA entre execucoes deste instalador.
    with open('/run/lock/brasil-saas-bi-cert.lock', 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        configure(args, account)


def ensure_identity(args):
    cert = args.pki / f'issued/{ROLE}.crt'
    key = args.pki / f'private/{ROLE}.key'
    if cert.exists() != key.exists():
        raise RuntimeError('Identidade BI parcial; recuperar a chave/certificado sem sobrescrever a PKI')
    if not cert.exists():
        env = clean_env(); env.update(EASYRSA_PKI=str(args.pki), EASYRSA_BATCH='1')
        run([args.easyrsa, 'build-client-full', ROLE, 'nopass'], env=env)
    run(['openssl', 'verify', '-purpose', 'sslclient', '-CAfile', args.pki / 'ca.crt', cert])
    run(['openssl', 'x509', '-in', cert, '-checkend', '86400', '-noout'])
    subject = run(['openssl', 'x509', '-in', cert, '-noout', '-subject', '-nameopt', 'RFC2253']).strip()
    if re.findall(r'(?:^subject=|,)CN=([^,]+)', subject) != [ROLE]:
        raise RuntimeError('CN do certificado nao corresponde exatamente a role BI')
    cert_pub = run(['openssl', 'x509', '-in', cert, '-noout', '-pubkey'])
    key_pub = run(['openssl', 'pkey', '-in', key, '-pubout'])
    if cert_pub != key_pub: raise RuntimeError('Certificado e chave BI nao correspondem')
    return cert, key


def configure(args, account):
    admin = ['runuser', '-u', 'postgres', '--', 'psql', '-X', '-w', '-v', 'ON_ERROR_STOP=1',
             '-h', args.pg_socket, '-p', str(args.pg_port), '-d', 'postgres', '-At', '-c']
    def sql(statement): return run(admin + [statement]).strip()
    if sql("SELECT count(*) FROM pg_database WHERE datname='brasil_saas_bi'") != '1':
        raise RuntimeError('Banco BI existente nao localizado; nao sera criado')
    if sql(f"SELECT count(*) FROM pg_roles WHERE rolname='{ROLE}' AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls") != '1':
        raise RuntimeError('Role BI ausente ou privilegios inadequados; nao sera alterada')
    if sql('SHOW ssl') != 'on': raise RuntimeError('TLS PostgreSQL nao habilitado')
    ca = Path(sql('SHOW ssl_ca_file'))
    if not ca.is_absolute(): ca = Path(sql('SHOW data_directory')) / ca
    if not ca.is_file() or fingerprint(ca) != fingerprint(args.pki / 'ca.crt'):
        raise RuntimeError('CA confiada pelo PostgreSQL difere da PKI existente')
    cert, key = ensure_identity(args)
    # Nunca copia a chave da CA nem qualquer identidade operacional.
    for directory in ('issued', 'private'):
        (args.cert_dir / directory).mkdir(parents=True, exist_ok=True)
    replace_file(args.cert_dir / f'issued/{ROLE}.crt', cert.read_bytes(), 0o644, 0, 0)
    pk8 = run(['openssl', 'pkcs8', '-topk8', '-inform', 'PEM', '-outform', 'DER',
               '-in', key, '-nocrypt'], binary=True)
    replace_file(args.cert_dir / f'private/{ROLE}.pk8', pk8, 0o600, account.pw_uid, account.pw_gid)
    # Libpq exige chave PEM; JDBC usa DER PKCS#8. Ambos exclusivos do BI.
    replace_file(args.cert_dir / f'private/{ROLE}.key', key.read_bytes(), 0o600, account.pw_uid, account.pw_gid)
    hba = Path(sql('SHOW hba_file')).resolve()
    original = hba.read_bytes(); stat = hba.stat()
    updated = managed_hba(original.decode()).encode()
    changed = updated != original
    if changed:
        backup = hba.with_name(hba.name + f'.bi-backup-{time.time_ns()}')
        replace_file(backup, original, 0o600, 0, 0)
        replace_file(hba, updated, stat.st_mode & 0o777, stat.st_uid, stat.st_gid)
    try:
        if sql('SELECT count(*) FROM pg_hba_file_rules WHERE error IS NOT NULL') != '0':
            raise RuntimeError('PostgreSQL rejeitou a sintaxe HBA; restaurando backup')
        if sql('SELECT pg_reload_conf()') != 't': raise RuntimeError('Reload PostgreSQL falhou')
        time.sleep(1)
        env = clean_env()
        env.update(PGHOST='127.0.0.1', PGPORT=str(args.pg_port), PGDATABASE='brasil_saas_bi',
                   PGUSER=ROLE, PGSSLMODE='verify-ca', PGSSLROOTCERT=str(args.cert_dir / 'ca.crt'),
                   PGSSLCERT=str(args.cert_dir / f'issued/{ROLE}.crt'),
                   PGSSLKEY=str(args.cert_dir / f'private/{ROLE}.key'), PGPASSFILE='/dev/null', PGCONNECT_TIMEOUT='5')
        proof = run(['runuser', '-u', args.app_user, '--', 'psql', '-X', '-w', '-At', '-v', 'ON_ERROR_STOP=1',
                     '-c', "SELECT current_user,current_database(),ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()"], env=env)
        if proof.strip() != f'{ROLE}|brasil_saas_bi|t': raise RuntimeError('Conexao libpq BI nao validada')
        # Uma regra trust ou scram anterior nao pode mascarar autenticação por certificado.
        no_cert = env.copy(); no_cert.update(PGSSLCERT='/nonexistent-bi-cert', PGSSLKEY='/nonexistent-bi-key')
        negative = subprocess.run(['runuser', '-u', args.app_user, '--', 'psql', '-X', '-w', '-At', '-c', 'SELECT 1'],
                                  env=no_cert, capture_output=True, timeout=15)
        if negative.returncode == 0: raise RuntimeError('Conexao sem certificado foi aceita; HBA incorreto')
        source = Path(__file__).with_name('BiJdbcProbe.java').resolve()
        probe = Path('/opt/brasil-saas-bi/BiJdbcProbe.java')
        probe.parent.mkdir(parents=True, exist_ok=True)
        replace_file(probe, source.read_bytes(), 0o644, 0, 0)
        print(run(['runuser', '-u', args.app_user, '--', 'java', '--class-path', args.jdbc_jar,
                   probe, '127.0.0.1', str(args.pg_port), args.cert_dir]), end='')
    except Exception:
        if changed:
            replace_file(hba, original, stat.st_mode & 0o777, stat.st_uid, stat.st_gid)
            sql('SELECT pg_reload_conf()')
        raise
    print('BI validado. Datasource continua desabilitado ate habilitacao explicita no ambiente do ERP.')


if __name__ == '__main__':
    try:
        main()
    except subprocess.CalledProcessError as error:
        # Nao imprimir argumentos/ambiente ou saida potencialmente sensivel.
        raise SystemExit(f'Comando {Path(str(error.cmd[0])).name} falhou (codigo {error.returncode}); BI nao habilitado')
