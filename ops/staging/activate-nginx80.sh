#!/usr/bin/env bash
# Add only the staging virtual host to the existing Nginx HTTP listener.
set -Eeuo pipefail
if [ "$(id -u)" -ne 0 ]; then
    echo 'Esta etapa precisa de administrador para editar e recarregar o Nginx.' >&2
    exit 1
fi
curl --fail --silent --show-error --max-time 10 http://127.0.0.1:18081/actuator/health \
    | python3 -c 'import json,sys; assert json.load(sys.stdin).get("status") == "UP"'
nginx -t
nginx -T 2>/dev/null | python3 -c 'import sys,re; s=sys.stdin.read(); assert re.search(r"include\s+/etc/nginx/conf\.d/\*\.conf\s*;",s), "Nginx precisa incluir /etc/nginx/conf.d/*.conf no contexto http"'
TARGET=/etc/nginx/conf.d/brasil-saas-staging.conf
BACKUP=$(mktemp /etc/nginx/conf.d/brasil-saas-staging.backup.XXXXXX)
HEADERS=$(mktemp)
HAD_CONFIG=false
if [ -e "$TARGET" ]; then
    test -f "$TARGET" && test ! -L "$TARGET"
    cp -p "$TARGET" "$BACKUP"
    HAD_CONFIG=true
fi
rollback() {
    trap - ERR
    if [ "$HAD_CONFIG" = true ]; then cp -p "$BACKUP" "$TARGET"; else rm -f "$TARGET"; fi
    nginx -t && systemctl reload nginx
    rm -f "$BACKUP" "$HEADERS"
}
trap rollback ERR
cat > "$TARGET" <<'NGINX'
# Staging enters on the same HTTP port 80; production's default host is preserved.
server {
    listen 80;
    listen [::]:80;
    server_name staging.brasil-saaserp.com;
    add_header X-ERP-Environment staging always;
    location / {
        proxy_pass http://127.0.0.1:18081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
        proxy_set_header Authorization $http_authorization;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $http_connection;
        proxy_read_timeout 300s;
    }
}
NGINX
chmod 0644 "$TARGET"
nginx -t
systemctl reload nginx
VERIFIED=false
for attempt in $(seq 1 10); do
    if curl --fail --silent --show-error --max-time 5 -D "$HEADERS" \
        -H 'Host: staging.brasil-saaserp.com' http://127.0.0.1/login -o /dev/null \
        && python3 -c 'import pathlib,sys; sys.exit(0 if any(line.lower().strip() == "x-erp-environment: staging" for line in pathlib.Path(sys.argv[1]).read_text().splitlines()) else 1)' "$HEADERS"; then
        VERIFIED=true
        break
    fi
    sleep 1
done
[ "$VERIFIED" = true ]
curl --fail --silent --show-error --max-time 10 http://127.0.0.1/login -o /dev/null
if id -u euripedes >/dev/null 2>&1; then
    loginctl enable-linger euripedes
fi
trap - ERR
rm -f "$BACKUP" "$HEADERS"
echo 'Staging e producao validados pelo Nginx na porta 80.'
