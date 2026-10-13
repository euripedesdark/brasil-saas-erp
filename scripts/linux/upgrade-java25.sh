#!/usr/bin/env bash
# Instala JDK25 e muda somente o runtime das duas units autorizadas.
set -Eeuo pipefail
MODE="${1:---check}"
case "$MODE" in --check|--install|--apply|--print-home) ;; *) echo 'Uso: upgrade-java25.sh --check|--install|--apply|--print-home'; exit 2 ;; esac

find_jdk() {
    local candidate version
    for candidate in "${ERP_JAVA_HOME:-}" "${JAVA_HOME:-}" /usr/lib/jvm/* /opt/java/*; do
        [[ -x "$candidate/bin/javac" && -x "$candidate/bin/java" ]] || continue
        version=$("$candidate/bin/javac" -version 2>&1)
        if [[ "$version" =~ ^javac\ 25(\.|$) ]]; then JDK="$candidate"; return 0; fi
    done
    return 1
}
if ! find_jdk; then
    if [[ "$MODE" != --install ]]; then echo '[ERRO] JDK25 ausente; execute sudo bash scripts/linux/upgrade-java25.sh --install.' >&2; exit 1; fi
    [[ "$EUID" == 0 ]] || { echo '[ERRO] Instalacao exige sudo.' >&2; exit 1; }
    PREVIOUS_JAVA="$(readlink -f "$(command -v java)" 2>/dev/null || true)"
    PREVIOUS_JAVAC="$(readlink -f "$(command -v javac)" 2>/dev/null || true)"
    if command -v dnf >/dev/null; then dnf install -y java-25-openjdk-devel
    elif command -v apt-get >/dev/null; then apt-get update; apt-get install -y openjdk-25-jdk
    else echo '[ERRO] Instale JDK25 oficial e informe ERP_JAVA_HOME.' >&2; exit 1; fi
    # Pacotes podem trocar alternatives automaticamente. Preservar o Java global
    # protege launchers de outros aplicativos; as duas units recebem drop-ins.
    for binary in java javac; do
        if [[ "$binary" == java ]]; then previous="$PREVIOUS_JAVA"; else previous="$PREVIOUS_JAVAC"; fi
        current="$(readlink -f "$(command -v "$binary")" 2>/dev/null || true)"
        if [[ -n "$previous" && -x "$previous" && "$current" != "$previous" ]]; then
            if command -v update-alternatives >/dev/null; then update-alternatives --set "$binary" "$previous"
            elif command -v alternatives >/dev/null; then alternatives --set "$binary" "$previous"
            else echo '[ERRO] Default global mudou; restaure-o antes de continuar.' >&2; exit 1; fi
        fi
    done
    find_jdk || { echo '[ERRO] Pacote instalado, mas JDK25 nao localizado.' >&2; exit 1; }
fi
if [[ "$MODE" == --print-home ]]; then printf '%s\n' "$JDK"; exit 0; fi
"$JDK/bin/java" -version
if [[ "$MODE" != --apply ]]; then
    echo '[OK] JDK25 disponivel. Use --apply para selecionar nas units; nao reinicia servicos.'
    exit 0
fi
[[ "$EUID" == 0 ]] || { echo '[ERRO] Alterar units exige sudo.' >&2; exit 1; }
python3 - "$JDK" <<'PY'
import datetime,os,pathlib,re,shlex,shutil,subprocess,sys,tempfile
jdk=sys.argv[1]; plans=[]
for unit in ('brasil-saas-erp.service','astral-platform.service'):
    value=subprocess.check_output(['systemctl','show',unit,'-p','ExecStart','--value'],text=True)
    found=re.search(r'argv\[\]=(.*?)\s+;\s+ignore_errors=',value)
    if not found: raise SystemExit('ExecStart nao reconhecido para '+unit+'; nenhuma unit alterada.')
    args=shlex.split(found.group(1))
    if not args or pathlib.Path(args[0]).name!='java' or '-jar' not in args or any('$' in a for a in args):
        raise SystemExit('ExecStart deve ser java -jar sem expansoes pendentes: '+unit)
    args[0]=str(pathlib.Path(jdk)/'bin/java')
    def quote(arg): return '"'+arg.replace('\\','\\\\').replace('"','\\"').replace('%','%%')+'"'
    content='[Service]\nEnvironment="JAVA_HOME='+jdk+'"\nExecStart=\nExecStart='+' '.join(map(quote,args))+'\n'
    target=pathlib.Path('/etc/systemd/system')/(unit+'.d')/'90-java25.conf'
    if target.is_symlink(): raise SystemExit('Drop-in nao pode ser link simbolico: '+unit)
    plans.append((target,content))
stamp=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
for target,content in plans:
    target.parent.mkdir(parents=True,exist_ok=True)
    if target.exists(): shutil.copy2(target,str(target)+'.bak.'+stamp)
    fd,name=tempfile.mkstemp(dir=target.parent,prefix='.java25-')
    with os.fdopen(fd,'w') as f: f.write(content)
    os.chmod(name,0o644);os.replace(name,target)
subprocess.run(['systemctl','daemon-reload'],check=True)
print('[OK] ERP e Astral selecionados para JDK25; Graylog e processos atuais preservados.')
print('Recompile/deploye ambos antes do proximo restart.')
PY
