#!/usr/bin/env python3
"""Validate and optionally install only the Brasil SaaS BI Graylog plugin."""
import argparse
import datetime
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

PLUGIN = 'br.com.brasil_saas.graylog.BrasilSaasBiPlugin'
NAME = 'brasil-saas-graylog-bi-1.0.0.jar'


def validate_jar(path):
    if path.stat().st_size > 20 * 1024 * 1024:
        raise ValueError('JAR maior que o esperado para um plugin de apresentacao')
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        service = jar.read('META-INF/services/org.graylog2.plugin.Plugin').decode().strip()
        if service != PLUGIN or PLUGIN.replace('.', '/') + '.class' not in names:
            raise ValueError('JAR nao identifica o plugin Brasil SaaS BI')
        info = jar.getinfo('plugin.' + PLUGIN + '.module.json')
        if info.file_size > 65536:
            raise ValueError('Manifesto web invalido')
        manifest = json.loads(jar.read(info))
        scripts = manifest.get('files', {}).get('js', [])
        if not scripts or not isinstance(scripts, list):
            raise ValueError('JAR sem bundle web')
        for entry in scripts:
            filename = entry.lstrip('/')
            if '..' in filename.split('/') or not filename.startswith('plugin.') or filename not in names:
                raise ValueError('Manifesto aponta para bundle ausente ou externo')
    return hashlib.sha256(path.read_bytes()).hexdigest()


def install(jar, directory, backup_dir):
    target = directory / NAME
    if target.is_symlink():
        raise ValueError('Destino nao pode ser um link simbolico')
    content = jar.read_bytes()
    if target.exists() and target.read_bytes() == content:
        return False
    if target.exists():
        backup_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S-%f')
        backup = backup_dir / (stamp + '-' + NAME)
        with backup.open('xb') as file:
            os.chmod(backup, 0o600); file.write(target.read_bytes())
    fd, temporary = tempfile.mkstemp(prefix='.brasil-saas-bi-', dir=directory)
    try:
        with os.fdopen(fd, 'wb') as file:
            file.write(content); file.flush(); os.fsync(file.fileno()); os.fchmod(file.fileno(), 0o644)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)
    return True


def configure_file(path, values, backup_dir):
    if path.is_symlink() or not set(values).issubset({'plugin_dir', 'http_external_uri'}):
        raise ValueError('Arquivo ou propriedades de configuracao invalidos')
    original = path.read_bytes()
    current = path.stat()
    seen = set()
    lines = []
    for line in original.decode('latin-1').splitlines(keepends=True):
        match = re.match(r'^\s*([a-z_]+)\s*[=:]', line)
        if match and match.group(1) in values:
            key = match.group(1); seen.add(key)
            newline = '\r\n' if line.endswith('\r\n') else '\n'
            line = key + ' = ' + values[key] + newline
        lines.append(line)
    for key in values.keys() - seen:
        if lines and not lines[-1].endswith('\n'): lines[-1] += '\n'
        lines.append(key + ' = ' + values[key] + '\n')
    content = ''.join(lines).encode('latin-1')
    if content == original: return False
    backup_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d-%H%M%S-%f')
    backup = backup_dir / (stamp + '-server.conf')
    with backup.open('xb') as file:
        os.chmod(backup, 0o600); file.write(original)
    fd, temporary = tempfile.mkstemp(prefix='.brasil-saas-bi-conf-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as file:
            file.write(content); file.flush(); os.fsync(file.fileno()); os.fchmod(file.fileno(), current.st_mode & 0o777)
            os.fchown(file.fileno(), current.st_uid, current.st_gid)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=pathlib.Path,
                        default=pathlib.Path(__file__).resolve().parents[2] / 'integrations/graylog-bi/target' / NAME)
    parser.add_argument('--config-file')
    parser.add_argument('--plugin-dir', type=pathlib.Path)
    parser.add_argument('--web-url', help='Endereco publico do Graylog a registrar no server.conf')
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--restart', action='store_true')
    args = parser.parse_args()
    if args.restart and not args.apply:
        raise ValueError('--restart exige --apply')
    sha = validate_jar(args.jar)
    spec = importlib.util.spec_from_file_location('graylog_setup', pathlib.Path(__file__).with_name('configure-graylog.py'))
    setup = importlib.util.module_from_spec(spec); spec.loader.exec_module(setup)
    discovered = setup.discover(args.config_file)
    web_url = setup.safe_url(args.web_url) if args.web_url else None
    if discovered['package_version'] != '7.1.9':
        raise ValueError('Instalacao validada apenas para Graylog 7.1.9; versao local diferente ou nao identificada')
    directory = args.plugin_dir
    if directory is None and discovered.get('config_source'):
        for line in pathlib.Path(discovered['config_source']).read_text().splitlines():
            if line.lstrip().startswith('#') or '=' not in line: continue
            key, value = line.split('=', 1)
            if key.strip() == 'plugin_dir': directory = pathlib.Path(value.strip()); break
    if directory is None:
        directory = next((pathlib.Path(path) for path in ['/usr/share/graylog-server/plugin', '/usr/share/graylog/plugin']
                          if pathlib.Path(path).is_dir()), None)
    if directory is None or not directory.is_absolute() or not directory.is_dir():
        raise ValueError('Pasta de plugins nao identificada; informe --plugin-dir com pasta absoluta existente')
    config = pathlib.Path(discovered['config_source']) if discovered.get('config_source') else None
    if web_url and config is None: raise ValueError('server.conf nao identificado para atualizar endereco web')
    result = {'graylog_version': discovered['package_version'], 'plugin_dir': str(directory), 'sha256': sha, 'installed': False, 'restarted': False}
    if args.apply:
        if os.geteuid() != 0: raise ValueError('Use sudo para instalar')
        result['changed'] = install(args.jar, directory, pathlib.Path('/var/backups/brasil-saas-graylog-bi'))
        values = {'plugin_dir': str(directory)}
        if web_url: values['http_external_uri'] = web_url
        result['config_changed'] = configure_file(config, values, pathlib.Path('/var/backups/brasil-saas-graylog-bi')) if config else False
        result['installed'] = True
        if args.restart:
            subprocess.run(['systemctl', 'restart', 'graylog-server'], check=True, timeout=90)
            state = setup.command(['systemctl', 'is-active', 'graylog-server'])
            result['service_active'] = state == 'active'; result['restarted'] = True
    print(json.dumps(result, indent=2))
    print('Valide a apresentacao pelo botao Indicadores no Graylog no ERP; servico ativo nao comprova consulta funcional.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError):
        print('Instalacao nao concluida. Verifique JAR, Graylog 7.1.9, pasta de plugins e permissoes.', file=sys.stderr)
        sys.exit(1)
