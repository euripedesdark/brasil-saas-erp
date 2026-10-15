#!/usr/bin/env bash
set -euo pipefail

ERP_SMOKE_IMAGE=${ERP_SMOKE_IMAGE:-brasil-saas-erp:ci}
ERP_SMOKE_CONTAINER="erp-runtime-smoke-${GITHUB_RUN_ID:-local}-${GITHUB_RUN_ATTEMPT:-1}"
ERP_SMOKE_REPORTS=${ERP_SMOKE_REPORTS:-runtime-smoke-reports}
ERP_SMOKE_URL=http://127.0.0.1:18080
ERP_SMOKE_STARTED=false
mkdir -p "$ERP_SMOKE_REPORTS"

cleanup() {
  ERP_SMOKE_RESULT=$?
  if [ "$ERP_SMOKE_STARTED" = true ]; then
    docker logs "$ERP_SMOKE_CONTAINER" > "$ERP_SMOKE_REPORTS/application.log" 2>&1 || true
    if [ "$ERP_SMOKE_RESULT" -ne 0 ]; then
      tail -n 160 "$ERP_SMOKE_REPORTS/application.log" >&2
    fi
    docker rm -f "$ERP_SMOKE_CONTAINER" > /dev/null || true
  fi
  exit "$ERP_SMOKE_RESULT"
}
trap cleanup EXIT

if curl --silent --max-time 1 "$ERP_SMOKE_URL/actuator/health" > /dev/null; then
  echo 'Porta 18080 ocupada: o teste exige uma porta livre.' >&2
  exit 1
fi

# O ERP conecta como "sa" (application.yml), o mesmo usuario do servico postgres
# deste job: a migracao V187 exige esse papel.
docker run --detach --name "$ERP_SMOKE_CONTAINER" --network host \
  --health-cmd 'curl -fsS http://localhost:18080/actuator/health || exit 1' \
  --env SERVER_PORT=18080 \
  --env SPRING_PROFILES_ACTIVE=ci-smoke \
  --env 'SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/erp_runtime_smoke?currentSchema=brasil_saas&sslmode=disable' \
  --env SPRING_DATASOURCE_USERNAME=sa \
  --env SPRING_DATASOURCE_PASSWORD=test123 \
  --env SPRING_DATA_MONGODB_URI=mongodb://127.0.0.1:27017/erp_runtime_smoke \
  --env SPRING_DATA_MONGODB_AUTO_INDEX_CREATION=false \
  --env SPRING_DATA_REDIS_HOST=127.0.0.1 \
  --env SPRING_DATA_REDIS_PORT=6379 \
  --env SPRING_RABBITMQ_HOST=127.0.0.1 \
  --env SPRING_RABBITMQ_PORT=5672 \
  --env SPRING_RABBITMQ_USERNAME=brasil-saas \
  --env SPRING_RABBITMQ_PASSWORD=test123 \
  --env SPRING_MAIL_HOST=127.0.0.1 \
  --env SPRING_MAIL_PORT=1025 \
  --env MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  --env MANAGEMENT_HEALTH_RABBIT_ENABLED=true \
  --env MANAGEMENT_HEALTH_REDIS_ENABLED=true \
  --env MANAGEMENT_HEALTH_MONGO_ENABLED=true \
  --env MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true \
  --env MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always \
  --env BRASIL_SAAS_BOOTSTRAP_ADMIN_ENABLED=false \
  --env BRASIL_SAAS_AUTH_SERVICE_ENABLED=false \
  --env BRASIL_SAAS_MIGRACAO_IMAGENS_ENABLED=false \
  --env JWT_SECRET=chave-jwt-apenas-do-ci-01234567890123456789012345678901234567890123456789 \
  --env APP_SECURITY_SECRET_ENCRYPTION_KEY=chave-cifra-apenas-do-ci \
  --env OPENAI_API_KEY=chave-apenas-do-ci \
  "$ERP_SMOKE_IMAGE"
ERP_SMOKE_STARTED=true

ERP_SMOKE_READY=false
for ERP_SMOKE_ATTEMPT in $(seq 1 90); do
  if [ "$(docker inspect --format '{{.State.Running}}' "$ERP_SMOKE_CONTAINER")" != true ]; then
    echo 'A aplicacao encerrou antes de ficar pronta.' >&2
    exit 1
  fi
  if curl --fail --silent --show-error --max-time 3 \
      "$ERP_SMOKE_URL/actuator/health" > "$ERP_SMOKE_REPORTS/health.json" 2> /dev/null; then
    ERP_SMOKE_READY=true
    break
  fi
  sleep 2
done
if [ "$ERP_SMOKE_READY" != true ]; then
  echo 'A aplicacao nao ficou pronta dentro do prazo.' >&2
  exit 1
fi

python3 - "$ERP_SMOKE_REPORTS/health.json" <<'PY'
import json
import sys
with open(sys.argv[1]) as source:
    health = json.load(source)
assert health['status'] == 'UP', health
for name in ('db', 'mongo', 'rabbit', 'redis', 'readinessState'):
    component = health.get('components', {}).get(name)
    assert component and component['status'] == 'UP', (name, component)
print('PASS aplicacao pronta e PostgreSQL/MongoDB/RabbitMQ/Redis disponiveis')
PY

curl --fail --silent --show-error --max-time 10 "$ERP_SMOKE_URL/" > "$ERP_SMOKE_REPORTS/frontend.html"
python3 - "$ERP_SMOKE_REPORTS/frontend.html" <<'PY'
from pathlib import Path
import sys
html = Path(sys.argv[1]).read_text().lower()
assert '<html' in html and '</html>' in html, 'O frontend nao retornou um documento HTML'
print('PASS frontend servido pela imagem do ERP')
PY
