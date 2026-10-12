#!/usr/bin/env bash
# Reutiliza o ETL manual existente. Nao cria/altera banco, role ou views.
set -Eeuo pipefail
[[ $(id -u) == 0 ]] || { echo 'Execute com sudo bash.' >&2; exit 1; }
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
command -v psql >/dev/null
command -v flock >/dev/null
command -v systemctl >/dev/null
getent passwd postgres >/dev/null
install -d -m 0755 /opt/brasil-saas-bi /etc/brasil-saas
install -m 0755 "$ROOT/sincronizar_reporting.sh" /opt/brasil-saas-bi/sincronizar_reporting.sh
# Preserva configuracao personalizada em reinstalacoes.
if [[ ! -e /etc/brasil-saas/bi-etl.env ]]; then
    install -m 0600 /dev/null /etc/brasil-saas/bi-etl.env
    cat > /etc/brasil-saas/bi-etl.env <<'ENV'
BI_SOURCE_DB=brasil-saas
BI_TARGET_DB=brasil_saas_bi
BI_ETL_LOCK_FILE=/run/lock/brasil-saas-bi/etl.lock
BI_ETL_STATEMENT_TIMEOUT_MS=300000
BI_ETL_LOCK_TIMEOUT_MS=5000
ENV
fi
install -m 0644 "$ROOT/systemd/brasil-saas-bi-etl.service" /etc/systemd/system/brasil-saas-bi-etl.service
install -m 0644 "$ROOT/systemd/brasil-saas-bi-etl.timer" /etc/systemd/system/brasil-saas-bi-etl.timer
printf '%s\n' 'd /run/lock/brasil-saas-bi 0750 postgres postgres -' > /etc/tmpfiles.d/brasil-saas-bi-etl.conf
systemd-tmpfiles --create /etc/tmpfiles.d/brasil-saas-bi-etl.conf
systemd-analyze verify /etc/systemd/system/brasil-saas-bi-etl.service /etc/systemd/system/brasil-saas-bi-etl.timer
systemctl daemon-reload
# O timer somente e habilitado depois de uma carga real bem-sucedida.
systemctl start brasil-saas-bi-etl.service
systemctl enable --now brasil-saas-bi-etl.timer
systemctl list-timers --all brasil-saas-bi-etl.timer --no-pager
echo 'ETL instalado. Consulte journalctl -u brasil-saas-bi-etl.service; diagnostico: systemctl start brasil-saas-bi-etl.service'
