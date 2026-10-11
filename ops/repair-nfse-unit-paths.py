#!/usr/bin/env python3
"""Repair only the three installed NFS-e unit paths; retain private settings."""
import os, pathlib, re, shutil, subprocess, time, urllib.request, datetime
ROOT = pathlib.Path('/home/euripedes/brasil-saas-erp')
OLD = '/home/euripedes/BrasilCloudERP'
NAMES = ['nfse-sp-api.service', 'nfse-sp-bridge.service', 'brasil_saas-watchdog.service']
def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
def main():
    if os.geteuid() != 0:
        raise SystemExit('Execute com sudo; as units pertencem ao root.')
    plans = []
    for name in NAMES:
        path = pathlib.Path('/etc/systemd/system') / name
        if not path.is_file() or path.is_symlink():
            raise SystemExit('Unit ausente ou link inesperado: ' + name)
        original = path.read_text()
        updated = original.replace(OLD, str(ROOT))
        for directory in re.findall(r'^WorkingDirectory=(.+)$', updated, re.M):
            if not pathlib.Path(directory).is_dir():
                raise SystemExit('Diretorio de trabalho ausente: ' + name)
        command = re.search(r'^ExecStart=(.+)$', updated, re.M)
        if not command:
            raise SystemExit('ExecStart ausente: ' + name)
        artifacts = re.findall(r'[^\s;"{}]+\.(?:jar|rb|sh)', command.group(1))
        if not artifacts or not all(pathlib.Path(p).is_file() for p in artifacts):
            raise SystemExit('Artefato ausente: ' + name)
        if OLD in updated:
            raise SystemExit('Caminho antigo restante: ' + name)
        plans.append((name, path, original, updated))
    backup = pathlib.Path('/var/backups/brasil-saas-unit-paths') / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup.mkdir(parents=True, mode=0o700)
    for name, path, original, updated in plans:
        shutil.copy2(path, backup/name)
        os.chmod(backup/name, 0o600)
    try:
        for name, path, original, updated in plans:
            if updated != original:
                # Keep owner, permissions and private environment entries.
                with path.open('w') as f:
                    f.write(updated)
                    f.flush()
                    os.fsync(f.fileno())
        run('systemd-analyze', 'verify', *(str(p) for _,p,_,_ in plans))
        run('systemctl', 'daemon-reload')
    except Exception:
        for name, path, _, _ in plans:
            shutil.copy2(backup/name, path)
        run('systemctl', 'daemon-reload')
        raise SystemExit('Validacao falhou; conteudo anterior restaurado. Backup: ' + str(backup))
    run('systemctl', 'reset-failed', *NAMES)
    run('systemctl', 'restart', 'nfse-sp-api.service', 'nfse-sp-bridge.service')
    run('systemctl', 'restart', 'brasil_saas-watchdog.service')
    print('Caminhos corrigidos; backup privado: ' + str(backup))
    healthy = False
    for _ in range(30):
        for port in (4567,4568,4569):
            try:
                with urllib.request.urlopen('http://127.0.0.1:%d/api/nfse-sp/status' % port, timeout=2) as response:
                    if response.status == 200:
                        print('NFS-e status HTTP 200 na porta', port)
                        healthy = True
            except Exception:
                pass
        if healthy:
            break
        time.sleep(2)
    for name in NAMES:
        state = subprocess.run(['systemctl','show',name,'-p','ActiveState','-p','SubState','-p','ExecMainStatus'],capture_output=True,text=True,check=True).stdout.strip()
        print(name, state.replace('\n',' | '))
    if not healthy:
        raise SystemExit('Caminhos corrigidos, mas NFS-e ainda sem status HTTP 200; verificar configuracao e logs privados.')
if __name__ == '__main__':
    main()
