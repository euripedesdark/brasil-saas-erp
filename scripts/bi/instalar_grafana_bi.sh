#!/usr/bin/env bash
# Instala o Grafana como motor de graficos do BI e liga no banco brasil_saas_bi.
# Sem Docker: usa o pacote nativo do Fedora.
set -Eeuo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY="$REPO/deploy/grafana"
DB_BI="brasil_saas_bi"
DB_ROLE="grafana_bi"
DB_PASS="${GRAFANA_BI_DB_PASSWORD:-Btf1_Mk3}"
AD_SVC="${GRAFANA_AD_SVC:-grafana-svc@srvcloud.cloud}"
AD_PASS="${GRAFANA_AD_SVC_PASSWORD:-GrafanaBI@2026Svc!}"

[[ $EUID -eq 0 ]] || { echo "Execute com sudo bash."; exit 1; }
[[ -d "$DEPLOY" ]] || { echo "Pasta de deploy do Grafana nao encontrada: $DEPLOY"; exit 1; }

echo "==> Grafana instalado?"
if ! rpm -q grafana >/dev/null 2>&1; then
  dnf install -y grafana
fi

echo "==> Role de leitura do banco BI ($DB_ROLE)"
su - postgres -c "psql -q -v ON_ERROR_STOP=1 -d $DB_BI" <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='$DB_ROLE') THEN
    CREATE ROLE $DB_ROLE LOGIN PASSWORD '$DB_PASS';
  END IF;
END \$\$;
GRANT CONNECT ON DATABASE $DB_BI TO $DB_ROLE;
GRANT USAGE ON SCHEMA brasil_saas_dl TO $DB_ROLE;
GRANT USAGE ON SCHEMA bi_reporting TO $DB_ROLE;
GRANT SELECT ON ALL TABLES IN SCHEMA brasil_saas_dl TO $DB_ROLE;
GRANT SELECT ON ALL TABLES IN SCHEMA bi_reporting TO $DB_ROLE;
ALTER DEFAULT PRIVILEGES IN SCHEMA brasil_saas_dl GRANT SELECT ON TABLES TO $DB_ROLE;
ALTER DEFAULT PRIVILEGES IN SCHEMA bi_reporting GRANT SELECT ON TABLES TO $DB_ROLE;
SQL

echo "==> Conta de servico do Grafana no AD"
if ! samba-tool user list | cut -d, -f1 | grep -qx "${AD_SVC%@*}"; then
  samba-tool user create "${AD_SVC%@*}" "$AD_PASS" \
    --description="Conta de servico do Grafana para consultar o AD" >/dev/null
  samba-tool user setpassword "${AD_SVC%@*}" --newpassword="$AD_PASS" >/dev/null
fi

echo "==> Provisioning: datasource e dashboard"
install -o root -g grafana -m 640 \
  "$DEPLOY/provisioning/datasources/bi-brasil-saas.yml" \
  /etc/grafana/provisioning/datasources/bi-brasil-saas.yml
install -o root -g grafana -m 644 \
  "$DEPLOY/provisioning/dashboards/bi.yaml" \
  /etc/grafana/provisioning/dashboards/bi.yaml
mkdir -p /var/lib/grafana/dashboards
install -o root -g grafana -m 644 "$DEPLOY/dashboards/bi-executive.json" \
  /var/lib/grafana/dashboards/bi-executive.json

echo "==> Grafana escuta so no loopback"
sed -i 's|^;http_addr = .*|http_addr = 127.0.0.1|' /etc/grafana/grafana.ini

echo "==> Autenticacao no AD"
install -o root -g grafana -m 640 "$DEPLOY/ldap.toml" /etc/grafana/ldap.toml
grep -q '^\[auth.ldap\]' /etc/grafana/grafana.ini || cat >> /etc/grafana/grafana.ini <<'INI'

[auth.ldap]
enabled = true
config_file = /etc/grafana/ldap.toml
allow_sign_up = true
sync_cron = "0 6 * * *"
sync_user_attr = userPrincipalName

[auth]
disable_login_form = false
INI

echo "==> Reiniciando o Grafana"
systemctl enable --now grafana-server
systemctl restart grafana-server
sleep 8
systemctl is-active --quiet grafana-server

echo "Grafana pronto. Dashboard: http://127.0.0.1:3000/d/bi-brasil-saas"
