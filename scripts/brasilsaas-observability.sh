#!/bin/bash
# Brasil SaaS ERP - Observability Setup Script
# Configura observabilidade completa com Graylog, Prometheus e Grafana

set -o errexit
set -o nounset
set -o pipefail

# =============================================================================
# CONFIGURACOES
# =============================================================================

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Diretorio base
# O caminho do repositorio NAO e fixo.
#
# A versao anterior apontava para
#   /home/euripedes/OneDrive/python/projetos-leno/JAVA/BRASIL-SAAS-ERP
# que e' o caminho DESTA maquina, e que nem existe aqui: o repo esta em
#   /home/euripedes/OneDrive/python/projetos-leno/GIT Repos/BRASIL-SAAS-ERP
#
# Em outra conta, outra pasta, ou com o OneDrive em outro lugar, o `cd` falhava
# e o script seguia rodando na pasta errada — sem mensagem, porque o `cd` sem
# `set -e` nao interrompe. Pior: as units systemd geradas gravavam o caminho
# fixo, entao o servico nao subia na maquina nova.
#
# O repositorio se localiza a partir do proprio script, que e' o unico ponto
# que muda junto com a copia de trabalho.
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# =============================================================================
# FUNCOES DE LOG
# =============================================================================

log() {
    local level=$1
    local message=$2
    echo -e "[${level}] ${message}"
}

log_info() {
    log "${GREEN}INFO${NC}" "$1"
}

log_warn() {
    log "${YELLOW}WARN${NC}" "$1"
}

log_error() {
    log "${RED}ERROR${NC}" "$1"
}

log_header() {
    echo -e "\n${BLUE}==========================================${NC}"
    echo -e "${BLUE} $1${NC}"
    echo -e "${BLUE}==========================================${NC}\n"
}

# =============================================================================
# FUNCOES DE INSTALACAO
# =============================================================================

# Instalar dependencias
install_dependencies() {
    log_header "Instalando Dependencias"
    
    # Atualizar pacotes
    log_info "Atualizando pacotes..."
    sudo apt-get update -qq
    
    # Instalar dependencias necessarias
    local deps=(
        "postgresql"
        "postgresql-client"
        "netcat-openbsd"
        "curl"
        "jq"
        "htop"
        "lsof"
        "sysstat"
        "net-tools"
    )
    
    for dep in "${deps[@]}"; do
        if ! dpkg -l | grep -q "$dep"; then
            log_info "Instalando $dep..."
            sudo apt-get install -y "$dep" -qq
        else
            log_info "$dep ja esta instalado"
        fi
    done
    
    log_info "Dependencias instaladas"
}

# Configurar Graylog
setup_graylog() {
    log_header "Configurando Graylog"
    
    local reuse_status=0
    reuse_observability_service graylog graylog-server.service || reuse_status=$?
    [[ "$reuse_status" == 0 ]] && return 0
    [[ "$reuse_status" == 1 ]] || return "$reuse_status"
    log_error "Graylog ausente. Use o instalador Astral atualizado; este script nao provisiona uma pilha paralela."
    return 1

 }

setup_mongodb() {
    local reuse_status=0
    reuse_observability_service mongo mongod.service || reuse_status=$?
    [[ "$reuse_status" == 0 ]] && return 0
    [[ "$reuse_status" == 1 ]] || return "$reuse_status"
    log_error "MongoDB ausente. Provisione uma unica instancia compativel com o Graylog instalado."
    return 1
}

# Configurar Prometheus
setup_prometheus() {
    log_header "Configurando Prometheus"
    
    # Verificar se Prometheus ja esta instalado
    if command -v prometheus &> /dev/null; then
        log_info "Prometheus ja esta instalado"
    else
        log_info "Instalando Prometheus..."
        
        # Criar usuario systemd
        sudo useradd --no-create-home --shell /bin/false prometheus
        
        # Criar diretorio
        sudo mkdir -p /etc/prometheus /var/lib/prometheus
        
        # Download Prometheus
        local version="2.47.0"
        cd /tmp
        wget -q "https://github.com/prometheus/prometheus/releases/download/v${version}/prometheus-${version}.linux-amd64.tar.gz"
        tar xfz "prometheus-${version}.linux-amd64.tar.gz"
        
        # Instalar
        sudo cp "prometheus-${version}.linux-amd64/prometheus" /usr/local/bin/
        sudo cp "prometheus-${version}.linux-amd64/promtool" /usr/local/bin/
        sudo cp -r "prometheus-${version}.linux-amd64/consoles" /etc/prometheus/
        sudo cp -r "prometheus-${version}.linux-amd64/console_libraries" /etc/prometheus/
        
        # Configurar
        sudo chown -R prometheus:prometheus /etc/prometheus /var/lib/prometheus
        
        # Criar systemd service
        cat > /tmp/prometheus.service << 'EOF'
[Unit]
Description=Prometheus
Wants=network-online.target
After=network-online.target

[Service]
User=prometheus
Group=prometheus
Type=simple
ExecStart=/usr/local/bin/prometheus \
    --config.file /etc/prometheus/prometheus.yml \
    --storage.tsdb.path /var/lib/prometheus \
    --web.console.templates=/etc/prometheus/consoles \
    --web.console.libraries=/etc/prometheus/console_libraries

[Install]
WantedBy=multi-user.target
EOF
        
        sudo cp /tmp/prometheus.service /etc/systemd/system/prometheus.service
        sudo systemctl daemon-reload
        sudo systemctl start prometheus
        sudo systemctl enable prometheus
        
        log_info "Prometheus instalado e configurado"
        log_info "Acesse: http://localhost:9090"
    fi
}

# Configurar Grafana
setup_grafana() {
    log_header "Configurando Grafana"
    
    # Verificar se Grafana ja esta instalado
    if command -v grafana-server &> /dev/null; then
        log_info "Grafana ja esta instalado"
    else
        log_info "Instalando Grafana..."
        
        # Adicionar repositorio Grafana
        sudo apt-get install -y apt-transport-https -qq
        sudo apt-get install -y software-properties-common -qq
        wget -qO - https://packages.grafana.com/gpg.key | sudo apt-key add -
        echo "deb https://packages.grafana.com/oss/deb stable main" | sudo tee /etc/apt/sources.list.d/grafana.list
        
        sudo apt-get update -qq
        sudo apt-get install -y grafana -qq
        
        # Iniciar Grafana
        sudo systemctl start grafana-server
        sudo systemctl enable grafana-server
        
        log_info "Grafana instalado e configurado"
        log_info "Acesse: http://localhost:3000"
        log_info "Usuario: admin"
        log_info "Senha: admin"
    fi
}

# Configurar Prometheus para monitorar Brasil SaaS
configure_prometheus() {
    log_header "Configurando Prometheus para Brasil SaaS"
    
    # Backup
    sudo cp /etc/prometheus/prometheus.yml /etc/prometheus/prometheus.yml.bak
    
    # Criar configuracao para Brasil SaaS
    cat > /tmp/brasil_saas.yml << 'EOF'
  - job_name: 'brasil_saas-erp'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets:
          - 'localhost:8081'  # core
          - 'localhost:8082'  # cadastro
          - 'localhost:8083'  # financeiro
          - 'localhost:8084'  # vendas
          - 'localhost:8085'  # compras
          - 'localhost:8086'  # estoque
          - 'localhost:8087'  # fiscal
          - 'localhost:8088'  # rh
          - 'localhost:8089'  # ia
          - 'localhost:8090'  # producao
          - 'localhost:8091'  # bi
EOF
    
    # Adicionar ao prometheus.yml
    sudo sed -i '/scrape_configs:/r /tmp/brasil_saas.yml' /etc/prometheus/prometheus.yml
    
    # Reiniciar Prometheus
    sudo systemctl restart prometheus
    
    log_info "Prometheus configurado para monitorar Brasil SaaS ERP"
}

# Configurar dashboards Grafana
configure_grafana_dashboards() {
    log_header "Configurando Dashboards Grafana"
    
    # Aguardar Grafana iniciar
    sleep 10
    
    # Criar datasource Prometheus
    curl -s -X POST http://admin:admin@localhost:3000/api/datasources \
        -H "Content-Type: application/json" \
        -d '{
            "name": "Prometheus",
            "type": "prometheus",
            "url": "http://localhost:9090",
            "access": "proxy",
            "isDefault": true
        }' > /dev/null
    
    log_info "Datasource Prometheus configurada no Grafana"
    
    # Importar dashboard Brasil SaaS (simplificado)
    local dashboard_json=$(cat << 'EOF'
{
  "dashboard": {
    "id": null,
    "title": "Brasil SaaS ERP Overview",
    "tags": ["brasil_saas", "erp"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Service Health",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job=\"brasil_saas-erp\"}",
            "refId": "A"
          }
        ]
      },
      {
        "id": 2,
        "title": "Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "jvm_memory_used_bytes{job=\"brasil_saas-erp\"}",
            "refId": "A"
          }
        ]
      },
      {
        "id": 3,
        "title": "HTTP Requests",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_server_requests_seconds_count{job=\"brasil_saas-erp\"}[5m])",
            "refId": "A"
          }
        ]
      }
    ]
  },
  "folderId": 0,
  "overwrite": false
}
EOF
    )
    
    curl -s -X POST http://admin:admin@localhost:3000/api/dashboards/import \
        -H "Content-Type: application/json" \
        -d "$dashboard_json" > /dev/null
    
    log_info "Dashboard Brasil SaaS ERP criado no Grafana"
}

# Configurar alertas
configure_alerts() {
    log_header "Configurando Alertas"
    
    # Criar regra de alerta para servicos inativos
    cat > /tmp/alert-rules.yml << 'EOF'
 groups:
   - name: brasil_saas-alerts
     rules:
       - alert: ServiceDown
         expr: up{job="brasil_saas-erp"} == 0
         for: 1m
         labels:
           severity: critical
         annotations:
           summary: "Servico Brasil SaaS ERP esta down"
           description: "O servico {{ $labels.instance }} esta inativo"
       
       - alert: HighMemoryUsage
         expr: jvm_memory_used_bytes{job="brasil_saas-erp"} / jvm_memory_max_bytes{job="brasil_saas-erp"} > 0.85
         for: 5m
         labels:
           severity: warning
         annotations:
           summary: "Alto uso de memoria JVM"
           description: "O modulo {{ $labels.instance }} esta usando mais de 85% da memoria JVM"
       
       - alert: HighErrorRate
         expr: rate(http_server_requests_seconds_count{job="brasil_saas-erp", status=~"5.."}[5m]) > 0.1
         for: 2m
         labels:
           severity: critical
         annotations:
           summary: "Alta taxa de erros HTTP"
           description: "O modulo {{ $labels.instance }} tem alta taxa de erros"
EOF
    
    sudo cp /tmp/alert-rules.yml /etc/prometheus/alert-rules.yml
    
    # Adicionar ao prometheus.yml
    sudo sed -i '/scrape_configs:/a\  rule_files:\n    - /etc/prometheus/alert-rules.yml' /etc/prometheus/prometheus.yml
    
    # Reiniciar Prometheus
    sudo systemctl restart prometheus
    
    log_info "Regra de alertas configuradas"
}

# Configurar Alertmanager
setup_alertmanager() {
    log_header "Configurando Alertmanager"
    
    # Instalar Alertmanager
    if command -v alertmanager &> /dev/null; then
        log_info "Alertmanager ja esta instalado"
    else
        log_info "Instalando Alertmanager..."
        
        local version="0.25.0"
        cd /tmp
        wget -q "https://github.com/prometheus/alertmanager/releases/download/v${version}/alertmanager-${version}.linux-amd64.tar.gz"
        tar xfz "alertmanager-${version}.linux-amd64.tar.gz"
        
        sudo cp "alertmanager-${version}.linux-amd64/alertmanager" /usr/local/bin/
        sudo cp "alertmanager-${version}.linux-amd64/amtool" /usr/local/bin/
        
        # Criar configuracao
        cat > /tmp/alertmanager.yml << 'EOF'
route:
  group_by: ['alertname']
  receiver: 'graylog'
  
receivers:
- name: 'graylog'
  webhook_configs:
  - url: 'http://localhost:9000/api/gelf'
EOF
        
        sudo mkdir -p /etc/alertmanager
        sudo cp /tmp/alertmanager.yml /etc/alertmanager/alertmanager.yml
        
        # Criar systemd service
        cat > /tmp/alertmanager.service << 'EOF'
[Unit]
Description=Alertmanager
After=network-online.target

[Service]
User=prometheus
Group=prometheus
ExecStart=/usr/local/bin/alertmanager \
    --config.file=/etc/alertmanager/alertmanager.yml

[Install]
WantedBy=multi-user.target
EOF
        
        sudo cp /tmp/alertmanager.service /etc/systemd/system/alertmanager.service
        sudo systemctl daemon-reload
        sudo systemctl start alertmanager
        sudo systemctl enable alertmanager
        
        log_info "Alertmanager instalado e configurado"
    fi
}

# =============================================================================
# FUNCAO PRINCIPAL
# =============================================================================

# Mudar para diretorio base
cd "$BASE_DIR" 2>/dev/null || {
    log_error "Diretorio base nao encontrado: $BASE_DIR"
    exit 1
}

# Verificar se esta rodando como root
if [ "$EUID" -ne 0 ]; then
    log_error "Este script requer privilégios de root"
    exit 1
fi

source "$BASE_DIR/scripts/shared-observability.sh"
observability_lock

# Parsing de argumentos
COMMAND="${1:-all}"

case "$COMMAND" in
    all)
        log_header "INICIANDO CONFIGURACAO COMPLETA DE OBSERVABILIDADE"
        
        install_dependencies
        setup_mongodb
        setup_graylog
        setup_prometheus
        setup_grafana
        
        configure_prometheus
        configure_alerts
        setup_alertmanager
        configure_grafana_dashboards
        
        log_header "CONFIGURACAO CONCLUIDA"
        log_info "Acesse:"
        log_info "  Graylog: endereco e credenciais da instalacao existente"
        log_info "  Prometheus: http://localhost:9090"
        log_info "  Grafana: http://localhost:3000 (admin/admin)"
        log_info "  Alertmanager: http://localhost:9093"
        ;;
    graylog)
        setup_mongodb
        setup_graylog
        ;;
    prometheus)
        setup_prometheus
        configure_prometheus
        configure_alerts
        ;;
    grafana)
        setup_grafana
        configure_grafana_dashboards
        ;;
    test)
        log_header "Testando conexoes"
        
        # Testar Elasticsearch
        if curl -s "http://localhost:9200" > /dev/null; then
            log_info "Elasticsearch: OK"
        else
            log_error "Elasticsearch: FAILED"
        fi
        
        # Testar Graylog
        if curl -s "http://localhost:9000" > /dev/null; then
            log_info "Graylog: OK"
        else
            log_error "Graylog: FAILED"
        fi
        
        # Testar Prometheus
        if curl -s "http://localhost:9090" > /dev/null; then
            log_info "Prometheus: OK"
        else
            log_error "Prometheus: FAILED"
        fi
        
        # Testar Grafana
        if curl -s "http://localhost:3000" > /dev/null; then
            log_info "Grafana: OK"
        else
            log_error "Grafana: FAILED"
        fi
        ;;
    --help|-h|help)
        echo "Uso: $0 [COMMAND]"
        echo ""
        echo "Comandos:"
        echo "  all         - Reutilizar MongoDB/Graylog e configurar Prometheus/Grafana"
        echo "  graylog    - Configurar apenas Graylog"
        echo "  prometheus - Configurar apenas Prometheus"
        echo "  grafana    - Configurar apenas Grafana"
        echo "  test       - Testar conexoes"
        echo ""
        exit 0
        ;;
    *)
        log_error "Comando nao reconhecido: $COMMAND"
        echo "Use $0 --help para ver opcoes"
        exit 1
        ;;
esac

exit 0
