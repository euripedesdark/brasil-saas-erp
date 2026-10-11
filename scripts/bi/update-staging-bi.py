#!/usr/bin/env python3
"""Compile inside isolated staging, replace its JAR and roll back on failure."""
import hashlib
import fcntl
import importlib.util
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile


def has_bi(path):
    try:
        with zipfile.ZipFile(path) as jar:
            names = jar.namelist()
            if 'BOOT-INF/classes/br/com/brasil_saas/bi/controller/GraylogBiController.class' not in names: return False
            return any(b'brasil-saas-bi-v1' in jar.read(name) for name in names
                       if name.startswith('BOOT-INF/classes/static/dist/assets/') and name.endswith('.js'))
    except (OSError, zipfile.BadZipFile): return False


def atomic_copy(source, target):
    if target.is_symlink(): raise ValueError('Destino staging nao pode ser link simbolico')
    fd, temporary = tempfile.mkstemp(prefix='.bi-jar-', dir=target.parent)
    try:
        with os.fdopen(fd, 'wb') as output, source.open('rb') as input_file:
            shutil.copyfileobj(input_file, output); output.flush(); os.fsync(output.fileno()); os.fchmod(output.fileno(), 0o600)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)


def main():
    if os.geteuid() == 0: raise ValueError('Execute como usuario euripedes, sem sudo')
    root = pathlib.Path.home() / '.local/share/brasil-saas-staging'
    lock = (root / 'bi-update.lock').open('a')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    envfile = root / 'staging.env'
    if envfile.stat().st_mode & 0o077: raise ValueError('Ambiente staging deve ser privado')
    settings = dict(line.split('=', 1) for line in envfile.read_text().splitlines() if '=' in line and not line.startswith('#'))
    spec = importlib.util.spec_from_file_location('validator', pathlib.Path(__file__).with_name('validate-staging-bi.py'))
    validator = importlib.util.module_from_spec(spec); spec.loader.exec_module(validator)
    validator.verify_isolated(settings)
    target = root / 'app.jar'
    if not target.is_file() or target.is_symlink(): raise ValueError('JAR staging anterior ausente ou invalido')
    if has_bi(target) and validator.validate(settings)['passed']:
        print('STAGING_BI_ALREADY_VALIDATED; NO_REBUILD_OR_RESTART'); return
    available = int(next(line.split()[1] for line in pathlib.Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemAvailable:')))
    if available < 900 * 1024: raise ValueError('Memoria disponivel insuficiente para compilar staging com margem (900 MiB)')
    if shutil.disk_usage(root).free < 2 * 1024 ** 3: raise ValueError('Espaco insuficiente para fontes e backup staging')
    source = pathlib.Path(__file__).resolve().parents[2]
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
    build = root / ('build-bi-' + sha[:12])
    if build.exists(): raise ValueError('Pasta de build ja existe; preservada para diagnostico')
    build.mkdir(mode=0o700)
    # Archive only tracked sources, never credentials or production runtime files.
    archive = root / ('sources-bi-' + sha[:12] + '.tar')
    with archive.open('xb') as file:
        os.chmod(archive, 0o600)
        subprocess.run(['git', 'archive', '--format=tar', 'HEAD'], cwd=source, stdout=file, check=True)
    subprocess.run(['tar', '-xf', str(archive), '-C', str(build)], check=True)
    archive.unlink()
    frontend = build / 'src/main/resources/static/react'
    compile_env = {**os.environ, 'MAVEN_OPTS': '-Xmx512m', 'NODE_OPTIONS': '--max-old-space-size=512'}
    subprocess.run(['npm', 'ci'], cwd=frontend, env=compile_env, check=True, timeout=180)
    subprocess.run(['npm', 'test'], cwd=frontend, env=compile_env, check=True, timeout=120)
    subprocess.run(['npm', 'run', 'build'], cwd=frontend, env=compile_env, check=True, timeout=180)
    subprocess.run(['mvn', '-B', '-ntp', 'clean', 'package', '-DskipTests'], cwd=build, env=compile_env, check=True, timeout=600)
    release = build / 'target/brasil-saas-erp-1.0.0-SNAPSHOT.jar'
    if not has_bi(release): raise ValueError('JAR compilado nao contem controlador e interface BI atualizados')
    backup_dir = root / 'bi-backups'; backup_dir.mkdir(exist_ok=True, mode=0o700)
    backup = backup_dir / ('app-' + str(time.time_ns()) + '.jar')
    shutil.copyfile(target, backup); backup.chmod(0o600)
    os.environ['XDG_RUNTIME_DIR'] = '/run/user/' + str(os.getuid())
    os.environ['DBUS_SESSION_BUS_ADDRESS'] = 'unix:path=' + os.environ['XDG_RUNTIME_DIR'] + '/bus'
    def service(action): subprocess.run(['systemctl', '--user', action, 'brasil-saas-staging-app.service'], check=True, timeout=45)
    def healthy():
        status, body = validator.request('/actuator/health')
        return status == 200 and isinstance(body, dict) and body.get('status') == 'UP'
    try:
        service('stop'); atomic_copy(release, target); service('start')
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            if healthy(): break
            time.sleep(3)
        else: raise ValueError('Staging nao confirmou health UP apos troca do JAR')
        report = validator.validate(settings)
        print(json.dumps(report, indent=2))
        if not report['passed'] or report.get('graylog_connection_http') != 200:
            raise ValueError('Contratos BI nao passaram apos atualizacao')
    except Exception:
        print('STAGING_BI_FAILED; RESTORING_PREVIOUS_JAR', file=sys.stderr)
        service('stop'); atomic_copy(backup, target); service('start')
        raise
    (root / 'bi-source.sha').write_text(sha + '\n')
    print('STAGING_BI_UPDATED', sha)
    print('STAGING_JAR_SHA256', hashlib.file_digest(target.open('rb'), 'sha256').hexdigest())
    print('Only staging updated. Graylog installation still requires the local activation command.')
    lock.close()


if __name__ == '__main__':
    try: main()
    except (OSError, ValueError, subprocess.SubprocessError):
        print('Atualizacao staging nao concluida; confira a etapa acima. Producao nao foi atualizada.', file=sys.stderr)
        sys.exit(1)
