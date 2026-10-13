#!/usr/bin/env bash
# Instala JDK25 e muda somente o runtime das duas units autorizadas.
# Sempre grava ExecStart com caminho ABSOLUTO do bin/java do JDK25.
# Se o drop-in anterior estiver quebrado (ex.: "bin/java"), remove-o,
# recarrega o systemd e reparseia a unit base antes de reescrever.
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
    if [[ "$MODE" != --install ]]; then
        echo '[ERRO] JDK25 ausente; execute sudo bash scripts/linux/upgrade-java25.sh --install.' >&2
        exit 1
    fi
    [[ "$EUID" == 0 ]] || { echo '[ERRO] Instalacao exige sudo.' >&2; exit 1; }
    PREVIOUS_JAVA="$(readlink -f "$(command -v java)" 2>/dev/null || true)"
    PREVIOUS_JAVAC="$(readlink -f "$(command -v javac)" 2>/dev/null || true)"
    if command -v dnf >/dev/null; then
        dnf install -y java-25-openjdk-devel
    elif command -v apt-get >/dev/null; then
        apt-get update
        apt-get install -y openjdk-25-jdk
    else
        echo '[ERRO] Instale JDK25 oficial e informe ERP_JAVA_HOME.' >&2
        exit 1
    fi
    # Pacotes podem trocar alternatives automaticamente. Preservar o Java global
    # protege launchers de outros aplicativos; as duas units recebem drop-ins.
    for binary in java javac; do
        if [[ "$binary" == java ]]; then previous="$PREVIOUS_JAVA"; else previous="$PREVIOUS_JAVAC"; fi
        current="$(readlink -f "$(command -v "$binary")" 2>/dev/null || true)"
        if [[ -n "$previous" && -x "$previous" && "$current" != "$previous" ]]; then
            if command -v update-alternatives >/dev/null; then
                update-alternatives --set "$binary" "$previous"
            elif command -v alternatives >/dev/null; then
                alternatives --set "$binary" "$previous"
            else
                echo '[ERRO] Default global mudou; restaure-o antes de continuar.' >&2
                exit 1
            fi
        fi
    done
    find_jdk || { echo '[ERRO] Pacote instalado, mas JDK25 nao localizado. Defina ERP_JAVA_HOME.' >&2; exit 1; }
fi

if [[ "$MODE" == --print-home ]]; then
    echo "$JDK"
    exit 0
fi
if [[ "$MODE" == --check ]]; then
    echo "[OK] JDK25 em $JDK"
    "$JDK/bin/java" -version
    exit 0
fi
if [[ "$MODE" == --install ]]; then
    echo "[OK] JDK25 pronto em $JDK"
    exit 0
fi

# --- --apply ---
[[ "$EUID" == 0 ]] || { echo '[ERRO] Alterar units exige sudo.' >&2; exit 1; }

# Garante path absoluto antes de passar ao Python
JDK=$(readlink -f "$JDK")
JAVA_BIN="$JDK/bin/java"
if [[ ! -x "$JAVA_BIN" || "$JAVA_BIN" != /* ]]; then
    echo "[ERRO] JAVA_BIN invalido: $JAVA_BIN" >&2
    exit 1
fi

# Se o drop-in anterior estiver quebrado, remove-o e recarrega para que
# systemctl show / cat voltem a refletir a unit base (com path absoluto).
for unit in brasil-saas-erp.service astral-platform.service; do
    dropin="/etc/systemd/system/${unit}.d/90-java25.conf"
    if [[ -f "$dropin" ]]; then
        if grep -qE 'ExecStart=["'\'']?bin/java|ExecStart=bin/java' "$dropin" 2>/dev/null \
           || ! grep -qE 'ExecStart=/' "$dropin" 2>/dev/null; then
            stamp=$(date +%Y%m%d%H%M%S)
            cp -a "$dropin" "${dropin}.bak.broken.${stamp}" 2>/dev/null || true
            rm -f "$dropin"
            echo "[WARN] drop-in quebrado removido: $dropin (backup .bak.broken.${stamp})"
        fi
    fi
done
systemctl daemon-reload 2>/dev/null || true

python3 - "$JDK" "$JAVA_BIN" <<'PY'
import datetime, os, pathlib, re, shlex, shutil, subprocess, sys, tempfile

jdk = os.path.realpath(sys.argv[1])
java_bin = os.path.realpath(sys.argv[2])

if not jdk.startswith("/") or not java_bin.startswith("/"):
    raise SystemExit(f"JDK/JAVA_BIN devem ser absolutos: jdk={jdk!r} java={java_bin!r}")
if not os.path.isfile(java_bin) or not os.access(java_bin, os.X_OK):
    raise SystemExit(f"java nao executavel: {java_bin}")

def quote(arg: str) -> str:
    # systemd: aspas duplas; % precisa ser %%
    return '"' + arg.replace("\\", "\\\\").replace('"', '\\"').replace("%", "%%") + '"'

# Fallbacks conhecidos quando a unit esta quebrada / sem ExecStart parseavel
KNOWN_FALLBACKS = {
    "brasil-saas-erp.service": [
        "-Xms256m", "-Xmx2g", "-XX:MaxMetaspaceSize=512m",
        "-Djava.security.egd=file:/dev/./urandom",
        "-jar", "/opt/brasil-saas-erp/brasil-saas-erp-1.0.0-SNAPSHOT.jar",
    ],
    "astral-platform.service": [
        "-jar", "/opt/astral-platform/astral-platform.jar",
    ],
}

def _extract_args_after_java(tokens: list[str]) -> list[str]:
    """Remove o binario java (qualquer path cujo basename seja java) e devolve o resto."""
    out = []
    skipped = False
    for t in tokens:
        if not skipped and pathlib.Path(t).name == "java":
            skipped = True
            continue
        out.append(t)
    if not skipped and tokens:
        # primeiro token era o java; descarta
        out = tokens[1:]
    return out

def parse_exec_args(unit: str) -> list[str]:
    """Extrai argumentos (flags + -jar + jar) do ExecStart efetivo ou da unit file."""
    # 1) systemctl show (funciona quando a unit esta valida)
    try:
        value = subprocess.check_output(
            ["systemctl", "show", unit, "-p", "ExecStart", "--value"],
            text=True, stderr=subprocess.DEVNULL,
        ).strip()
    except subprocess.CalledProcessError:
        value = ""

    args: list[str] = []
    if value and value != "":
        found = re.search(r"argv\[\]=(.*?)\s*;\s*ignore_errors=", value)
        if found:
            args = shlex.split(found.group(1))
        else:
            path_m = re.search(r"path=(\S+)", value)
            if path_m:
                jar_m = re.search(r"(-jar\s+\S+.*)$", value)
                if jar_m:
                    rest = shlex.split(jar_m.group(1))
                    args = [path_m.group(1)] + rest

    # 2) systemctl cat (lê o unit file + drop-ins; funciona mesmo se inválido às vezes)
    if not args or not any(pathlib.Path(a).name == "java" or a == "-jar" for a in args):
        try:
            cat_out = subprocess.check_output(
                ["systemctl", "cat", unit], text=True, stderr=subprocess.DEVNULL,
            )
            # Pega a última linha ExecStart= que não seja só "ExecStart=" (reset)
            last = None
            for line in cat_out.splitlines():
                s = line.strip()
                if s.startswith("ExecStart=") and s != "ExecStart=":
                    last = s[len("ExecStart="):].strip()
            if last:
                args = shlex.split(last)
        except (subprocess.CalledProcessError, ValueError):
            pass

    # 3) Fallback hardcoded
    if not args or "-jar" not in args:
        fb = KNOWN_FALLBACKS.get(unit)
        if fb:
            print(f"[WARN] usando fallback conhecido para {unit}", file=sys.stderr)
            return list(fb)
        raise SystemExit(
            f"ExecStart nao reconhecido para {unit}. "
            f"systemctl show:\n{value[:500]}\nNenhuma unit alterada."
        )

    # Devolve só os args depois do binário java
    rest = _extract_args_after_java(args)
    if "-jar" not in rest:
        raise SystemExit(f"ExecStart de {unit} nao tem -jar apos limpeza: {rest!r}")
    if any("$" in a for a in rest):
        raise SystemExit(
            f"ExecStart de {unit} tem expansao pendente ($VAR). "
            f"Use caminho literal no unit file: {rest!r}"
        )
    return rest

plans = []
for unit in ("brasil-saas-erp.service", "astral-platform.service"):
    rest = parse_exec_args(unit)
    # Sempre: java absoluto + args
    args = [java_bin] + rest

    if not args[0].startswith("/"):
        raise SystemExit(f"ExecStart gerado com path relativo (recusado): {args[0]!r}")
    if not os.path.isfile(args[0]):
        raise SystemExit(f"java binario inexistente: {args[0]!r}")

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
            exe = shlex.split(line[len("ExecStart="):])[0].strip('"')
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
    fd, name = tempfile.mkstemp(dir=str(target.parent), prefix=".java25-")
    with os.fdopen(fd, "w") as f:
        f.write(content)
    os.chmod(name, 0o644)
    os.replace(name, target)
    print(f"[OK] drop-in escrito: {target}")
    print(content.rstrip())
    print()

subprocess.run(["systemctl", "daemon-reload"], check=True)
print("[OK] ERP e Astral selecionados para JDK25; Graylog e processos atuais preservados.")
print("Recompile/deploye ambos antes do proximo restart.")
print("Para validar: systemctl cat brasil-saas-erp.service | grep -A2 ExecStart")
PY
