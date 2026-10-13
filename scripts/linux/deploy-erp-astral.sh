#!/usr/bin/env bash
# Atualiza e recompila ambos; valida Java25 antes de selecionar os runtimes.
set -Eeuo pipefail
trap 'echo "[ERRO] Linha $LINENO: $BASH_COMMAND" >&2' ERR
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ERP="${ERP_REPO_DIR:-$HOME/brasil-saas-erp}"
ASTRAL="${ASTRAL_REPO_DIR:-$HOME/astral-plataform-hci}"
ERP_JAR="${ERP_DEPLOY_JAR:-/opt/brasil-saas-erp/brasil-saas-erp-1.0.0-SNAPSHOT.jar}"
ASTRAL_JAR="${ASTRAL_DEPLOY_JAR:-/opt/astral-platform/astral-platform.jar}"
STAMP="$(date +%Y%m%d-%H%M%S)"
sudo -v
SELECTED_JDK="$(bash "$SCRIPT_DIR/upgrade-java25.sh" --print-home)"
export JAVA_HOME="$SELECTED_JDK"
export PATH="$JAVA_HOME/bin:$PATH"
command -v mvn >/dev/null; command -v npm >/dev/null

for repo in "$ERP" "$ASTRAL"; do
    [[ -d "$repo/.git" || -f "$repo/.git" ]] || { echo "[ERRO] Repositorio ausente: $repo"; exit 1; }
    dirty=$(git -C "$repo" status --porcelain -- . ':!src/main/resources/static/dist' ':!src/main/resources/static/app')
    [[ -z "$dirty" ]] || { echo "[ERRO] Mudancas locais em $repo. Commit/stash antes do deploy."; exit 1; }
    git -C "$repo" branch "backup-deploy-$STAMP"
    git -C "$repo" pull --no-rebase --no-edit origin main
done

for pair in "brasil-saas-erp.service|$ERP_JAR" "astral-platform.service|$ASTRAL_JAR"; do
    unit="${pair%%|*}"; jar="${pair#*|}"
    sudo test -f "$jar"
    if ! sudo systemctl show "$unit" -p ExecStart --value | grep -Fq -- "$jar"; then
        echo "[ERRO] JAR nao corresponde a $unit"; exit 1
    fi
done
sudo cp -a "$ERP_JAR" "$ERP_JAR.bak.$STAMP"
sudo cp -a "$ASTRAL_JAR" "$ASTRAL_JAR.bak.$STAMP"
ACTIVE=()
for unit in brasil-saas-erp.service astral-platform.service; do
    if sudo systemctl is-active --quiet "$unit"; then ACTIVE+=("$unit"); fi
    drop="/etc/systemd/system/$unit.d/90-java25.conf"
    if sudo test -f "$drop"; then sudo cp -a "$drop" "$drop.bak.$STAMP"; fi
done

rollback() {
    local result=$?
    trap - ERR
    echo '[ERRO] Restaurando JARs, drop-ins e estado anterior dos servicos.' >&2
    sudo systemctl stop brasil-saas-erp.service astral-platform.service || true
    sudo cp -a "$ERP_JAR.bak.$STAMP" "$ERP_JAR"
    sudo cp -a "$ASTRAL_JAR.bak.$STAMP" "$ASTRAL_JAR"
    for unit in brasil-saas-erp.service astral-platform.service; do
        drop="/etc/systemd/system/$unit.d/90-java25.conf"
        if sudo test -f "$drop.bak.$STAMP"; then sudo cp -a "$drop.bak.$STAMP" "$drop"
        elif (( RUNTIME_APPLIED )); then sudo rm -f -- "$drop"; fi
    done
    sudo systemctl daemon-reload
    if (( ${#ACTIVE[@]} )); then sudo systemctl start "${ACTIVE[@]}" || true; fi
    exit "${result:-1}"
}
RUNTIME_APPLIED=0
trap rollback ERR
sudo systemctl stop brasil-saas-erp.service astral-platform.service

build_repo() {
    local repo="$1" frontend="$2"
    # Falhas dentro da subshell sao tratadas uma unica vez pelo chamador.
    (
        trap - ERR
        cd "$repo"
        npm ci --prefix "$frontend"
        npm run build --prefix "$frontend"
        mvn -B clean verify
    )
}
build_repo "$ERP" src/main/resources/static/react
build_repo "$ASTRAL" frontend

jar_for() {
    local jars=()
    mapfile -t jars < <(find "$1/target" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*')
    [[ ${#jars[@]} == 1 ]] || { echo "[ERRO] JAR ambiguo em $1/target" >&2; return 1; }
    printf '%s\n' "${jars[0]}"
}
if ! ERP_BUILD="$(jar_for "$ERP")"; then false; fi
if ! ASTRAL_BUILD="$(jar_for "$ASTRAL")"; then false; fi
sudo cp "$ERP_BUILD" "$ERP_JAR"
sudo cp "$ASTRAL_BUILD" "$ASTRAL_JAR"
RUNTIME_APPLIED=1
sudo env ERP_JAVA_HOME="$JAVA_HOME" bash "$SCRIPT_DIR/upgrade-java25.sh" --apply
sudo systemctl start brasil-saas-erp.service astral-platform.service
sleep 15
sudo systemctl is-active --quiet brasil-saas-erp.service
sudo systemctl is-active --quiet astral-platform.service
trap - ERR
sudo sha256sum "$ERP_BUILD" "$ERP_JAR" "$ASTRAL_BUILD" "$ASTRAL_JAR"
sudo systemctl status brasil-saas-erp.service astral-platform.service --no-pager -l
echo '[OK] JARs publicados e processos ativos. Confirme readiness e login nas duas aplicacoes.'
