#!/usr/bin/env bash
# Instala JDK25 e muda somente o runtime das duas units autorizadas.
# Versão: 2026-10-13-v3
# Sempre grava ExecStart com caminho ABSOLUTO do bin/java do JDK25.
# Em --apply: remove TODOS os 90-java25.conf, daemon-reload, e recria do zero.
set -Eeuo pipefail
MODE="${1:---check}"
case "$MODE" in --check|--install|--apply|--print-home) ;; *) echo 'Uso: upgrade-java25.sh --check|--install|--apply|--print-home'; exit 2 ;; esac

find_jdk() {
    local candidate version resolved
    for candidate in "${ERP_JAVA_HOME:-}" "${JAVA_HOME:-}" /usr/lib/jvm/* /opt/java/*; do
        [[ -n "$candidate" && -x "$candidate/bin/javac" && -x "$candidate/bin/java" ]] || continue
        version=$("$candidate/bin/javac" -version 2>&1)
        if [[ "$version" =~ ^javac\ 25(\.|$) ]]; then
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

# --- --apply (v3) ---
echo "[upgrade-java25] versao 2026-10-13-v3  MODE=--apply"
[[ "$EUID" == 0 ]] || { echo '[ERRO] Alterar units exige sudo.' >&2; exit 1; }

JDK=$(readlink -f "$JDK")
JAVA_BIN="$JDK/bin/java"
if [[ ! -x "$JAVA_BIN" || "$JAVA_BIN" != /* ]]; then
    echo "[ERRO] JAVA_BIN invalido: $JAVA_BIN" >&2
    exit 1
fi
echo "[OK] JAVA_BIN=$JAVA_BIN"

# SEMPRE remove os drop-ins 90-java25.conf (quebrados ou nao) e recarrega,
# para que a unit base volte a ser a unica fonte de ExecStart.
stamp=$(date +%Y%m%d%H%M%S)
for unit in brasil-saas-erp.service astral-platform.service; do
    dropdir="/etc/systemd/system/${unit}.d"
    dropin="${dropdir}/90-java25.conf"
    if [[ -f "$dropin" || -L "$dropin" ]]; then
        mkdir -p "$dropdir"
        cp -a "$dropin" "${dropin}.bak.${stamp}" 2>/dev/null || true
        rm -f "$dropin"
        echo "[OK] drop-in removido: $dropin (backup .bak.${stamp})"
    fi
done
systemctl daemon-reload

# Args JVM + jar conhecidos (fonte da verdade se o parse falhar)
ERP_ARGS='-Xms256m -Xmx2g -XX:MaxMetaspaceSize=512m -Djava.security.egd=file:/dev/./urandom -jar /opt/brasil-saas-erp/brasil-saas-erp-1.0.0-SNAPSHOT.jar'
ASTRAL_ARGS='-jar /opt/astral-platform/astral-platform.jar'

write_dropin() {
    local unit="$1"
    local extra_args="$2"
    local dropdir="/etc/systemd/system/${unit}.d"
    local dropin="${dropdir}/90-java25.conf"
    mkdir -p "$dropdir"
    # Monta linha ExecStart com path absoluto, cada arg entre aspas
    local exec_line="ExecStart=\"${JAVA_BIN}\""
    # shellcheck disable=SC2086
    for a in $extra_args; do
        exec_line="${exec_line} \"${a}\""
    done
    cat > "$dropin" <<EOF
[Service]
Environment="JAVA_HOME=${JDK}"
Environment="PATH=${JDK}/bin:/usr/local/bin:/usr/bin:/bin"
ExecStart=
${exec_line}
EOF
    chmod 644 "$dropin"
    echo "[OK] drop-in escrito: $dropin"
    cat "$dropin"
    echo
}

write_dropin "brasil-saas-erp.service" "$ERP_ARGS"
write_dropin "astral-platform.service" "$ASTRAL_ARGS"

systemctl daemon-reload

# Validacao final: systemctl deve aceitar a unit
if ! systemctl cat brasil-saas-erp.service >/dev/null 2>&1; then
    echo "[ERRO] brasil-saas-erp.service ainda invalida apos drop-in" >&2
    systemctl cat brasil-saas-erp.service 2>&1 | head -20 || true
    exit 1
fi
if ! systemctl cat astral-platform.service >/dev/null 2>&1; then
    echo "[ERRO] astral-platform.service ainda invalida apos drop-in" >&2
    exit 1
fi

echo "[OK] ERP e Astral selecionados para JDK25; Graylog e processos atuais preservados."
echo "Recompile/deploye ambos antes do proximo restart."
echo "Para validar: systemctl cat brasil-saas-erp.service | grep -A3 ExecStart"
