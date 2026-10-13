#!/usr/bin/env bash
# Instala JDK25 e muda somente o runtime das duas units autorizadas.
set -Eeuo pipefail
MODE="${1:---check}"
case "$MODE" in --check|--install|--apply|--print-home) ;; *) echo 'Uso: upgrade-java25.sh --check|--install|--apply|--print-home'; exit 2 ;; esac

find_jdk() {
    local candidate version resolved
    for candidate in "${ERP_JAVA_HOME:-}" "${JAVA_HOME:-}" /usr/lib/jvm/* /opt/java/*; do
        [[ -n "$candidate" && -x "$candidate/bin/javac" && -x "$candidate/bin/java" ]] || continue
        version=$("$candidate/bin/javac" -version 2>&1)
        if [[ "$version" =~ ^javac\ 25(\.|$) ]]; then
            # Sempre caminho absoluto real (evita drop-in com "bin/java")
            resolved=$(readlink -f "$candidate" 2>/dev/null || realpath "$candidate" 2>/dev/null || echo "$candidate")
            if [[ "$resolved" != /* ]]; then
                continue
            fi
            JDK="$resolved"
            return 0
        fi
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
    echo '[OK] JDK25 disponivel em '"$JDK"'. Use --apply para selecionar nas units; nao reinicia servicos.'
    exit 0
fi
[[ "$EUID" == 0 ]] || { echo '[ERRO] Alterar units exige sudo.' >&2; exit 1; }

# Garante path absoluto antes de passar ao Python
JDK=$(readlink -f "$JDK")
JAVA_BIN="$JDK/bin/java"
if [[ ! -x "$JAVA_BIN" || "$JAVA_BIN" != /* ]]; then
    echo "[ERRO] JAVA_BIN invalido: $JAVA_BIN" >&2
    exit 1
fi

python3 - "$JDK" "$JAVA_BIN" <<'PY'
import datetime, os, pathlib, re, shlex, shutil, subprocess, sys, tempfile

jdk = os.path.realpath(sys.argv[1])
java_bin = os.path.realpath(sys.argv[2])

if not jdk.startswith("/") or not java_bin.startswith("/"):
    raise SystemExit(f"JDK/JAVA_BIN devem ser absolutos: jdk={jdk!r} java={java_bin!r}")
if not os.path.isfile(java_bin) or not os.access(java_bin, os.X_OK):
    raise SystemExit(f"java nao executavel: {java_bin}")

def quote(arg: str) -> str:
    return '"' + arg.replace("\\", "\\\\").replace('"', '\\"').replace("%", "%%") + '"'

def parse_exec_start(unit: str) -> list[str]:
    """Extrai argv do ExecStart via systemctl show (formato com argv[]=...)."""
    value = subprocess.check_output(
        ["systemctl", "show", unit, "-p", "ExecStart", "--value"], text=True
    ).strip()
    # Formato típico:
    #   { path=/usr/bin/java ; argv[]=/usr/bin/java -jar /opt/...jar ; ignore_errors=no ; ...}
    found = re.search(r"argv\[\]=(.*?)\s*;\s*ignore_errors=", value)
    if found:
        args = shlex.split(found.group(1))
        if args:
            return args
    # Fallback: path= no início
    path_m = re.search(r"path=(\S+)", value)
    if path_m:
        # Sem argv completo: só substitui o binário, mantém -jar se existir no valor
        jar_m = re.search(r"(-jar\s+\S+.*)$", value)
        if jar_m:
            rest = shlex.split(jar_m.group(1))
            return [path_m.group(1)] + rest
    raise SystemExit(
        f"ExecStart nao reconhecido para {unit}. "
        f"Saida de systemctl show:\n{value[:500]}\nNenhuma unit alterada."
    )

plans = []
for unit in ("brasil-saas-erp.service", "astral-platform.service"):
    args = parse_exec_start(unit)
    # Aceita java direto ou via env/bash, mas exige -jar e sem $VAR pendente
    has_jar = "-jar" in args
    has_dollar = any("$" in a for a in args)
    if not has_jar:
        raise SystemExit(f"ExecStart de {unit} nao tem -jar: {args!r}")
    if has_dollar:
        raise SystemExit(
            f"ExecStart de {unit} tem expansao pendente ($VAR). "
            f"Use caminho literal no unit file: {args!r}"
        )
    # Substitui o binário java (primeiro arg cujo nome é java, ou o [0])
    replaced = False
    for i, a in enumerate(args):
        if pathlib.Path(a).name == "java":
            args[i] = java_bin
            replaced = True
            break
    if not replaced:
        args[0] = java_bin

    if not args[0].startswith("/"):
        raise SystemExit(f"ExecStart gerado com path relativo (recusado): {args[0]!r}")

    content = (
        "[Service]\n"
        f'Environment="JAVA_HOME={jdk}"\n'
        f'Environment="PATH={jdk}/bin:/usr/local/bin:/usr/bin:/bin"\n'
        "ExecStart=\n"
        "ExecStart=" + " ".join(map(quote, args)) + "\n"
    )
    # Validação final do conteúdo
    for line in content.splitlines():
        if line.startswith("ExecStart=") and line != "ExecStart=":
            exe = shlex.split(line[len("ExecStart=") :])[0].strip('"')
            if not exe.startswith("/"):
                raise SystemExit(f"Drop-in invalido (path relativo): {exe!r}")
            if not os.path.isfile(exe):
                raise SystemExit(f"Drop-in aponta para binario inexistente: {exe!r}")

    target = pathlib.Path("/etc/systemd/system") / f"{unit}.d" / "90-java25.conf"
    if target.is_symlink():
        raise SystemExit(f"Drop-in nao pode ser link simbolico: {unit}")
    plans.append((target, content))

stamp = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
for target, content in plans:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        shutil.copy2(target, str(target) + ".bak." + stamp)
    fd, name = tempfile.mkstemp(dir=target.parent, prefix=".java25-")
    with os.fdopen(fd, "w") as f:
        f.write(content)
    os.chmod(name, 0o644)
    os.replace(name, target)
    print(f"[OK] drop-in escrito: {target}")
    print(content.rstrip())

subprocess.run(["systemctl", "daemon-reload"], check=True)
print("[OK] ERP e Astral selecionados para JDK25; Graylog e processos atuais preservados.")
print("Recompile/deploye ambos antes do proximo restart.")
print("Para validar: systemctl cat brasil-saas-erp.service | grep -A2 ExecStart")
PY
