#!/bin/bash
# =============================================================================
# Brasil SaaS ERP - Script de Instalação Base (Linux/Unix) — SEM DOCKER
# Compatível com: Ubuntu, Debian, CentOS, Fedora, RHEL, Arch
# Requisitos: JDK 21+, Node.js 20+, Maven 3.9+, Git
# Infra nativa: PostgreSQL, Redis, RabbitMQ, MinIO (serviços systemd)
# =============================================================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ONDE ESTE SCRIPT ESTA, E POR QUE E' AQUI EM CIMA.
#
# O PROJECT_DIR e' o caminho do repositorio, derivado do proprio script, e nao
# de um argumento nem do diretorio de onde o script foi chamado. E o que permite
# rodar o instalador de qualquer lugar, e em uma maquina onde o repositorio esta
# em outro caminho.
#
# Ele precisa estar definido ANTES de qualquer bloco que use. A versao anterior
# definia aqui embaixo, na linha 1512, e o bloco do proxy da NFS-e — que esta na
# 1212 — usava a variavel antes dela existir. O resultado foi:
#
#   [FAILOVER] nfse-failover.rb nao esta em /src/main/resources/microservices/nfse-failover
#
# O caminho sem prefixo, com barra na frente, vindo da raiz do sistema. E o
# sintoma engana: parece que o arquivo do proxy nao veio no clone, quando na
# verdade o script procurou no lugar errado.
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# -----------------------------------------------------------------------------
# A ENTRADA DO ERP PELO NGINX (:80 -> :8080) — LIGADA POR PADRAO
# -----------------------------------------------------------------------------
# O dono pediu o nginx como porta de entrada: acesso na 80, o nginx repassa
# para o Tomcat na 8080. A maquina do erp e' SO para o ERP, e o dono vai subir
# o astral mais tarde — sem um nginx na frente, cada aplicacao nova traz a
# discussao de porta de volta, e e' exatamente isso que a entrada resolve.
#
# A chave existe por seguranca, e nasce em 1 porque e' o estado desejado. O
# default ligado NAO derruba o nginx nunca: se a 80 estiver ocupada por outra
# coisa, o instalador avisa, nao escreve a conf, e sobe o proxy da NFS-e na 4567
# assim mesmo. A 80 e' opcional de fato, e nao de fachada.
#
# Para deixar o ERP so na 8080, sem entrada pelo nginx:
#
#     ENTRAR_PELO_NGINX=0 sudo ./installbase.sh
#
# O TLS entra no nginx sem tocar em Java, porque a 8080 ja e' o destino e o
# X-Forwarded-Proto e' o que o ERP le. O certificado esta adiado por decisao do
# dono; ate la, a 80 e' texto puro, e a 8080 continua valendo para uso local.
#
# NOTA SOBRE O QUE JA OCUPOU A 80
#
# No erp a 80 era do Nextcloud (snap.nextcloud.apache.service), com um usuario
# e so o conteudo padrao de fabrica, sem log de acesso, respondendo em HTTP
# puro para a rede. O dono nao o usava e mandou remover. Vale registrar porque
# o bloco pergunta quem esta na 80 antes de mexer, e a resposta foi um
# httpd de snap — nao o apache do sistema, que nem estava instalado.
# -----------------------------------------------------------------------------
ENTRAR_PELO_NGINX="${ENTRAR_PELO_NGINX:-1}"

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  Brasil SaaS ERP - Instalador de Ambiente NATIVO${NC}"
echo -e "${BLUE}  Spring Boot conforme pom.xml + React (Vite) — sem Docker${NC}"
echo -e "${BLUE}============================================================${NC}"

detect_distro() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        echo "$ID"
    elif type lsb_release >/dev/null 2>&1; then
        lsb_release -si | tr '[:upper:]' '[:lower:]'
    else
        uname -s | tr '[:upper:]' '[:lower:]'
    fi
}

DISTRO=$(detect_distro)
echo -e "Distribuição detectada: ${YELLOW}$DISTRO${NC}"

install_packages() {
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            echo -e "${GREEN}[APT] Atualizando repositórios...${NC}"
            apt-get update -y
            apt-get install -y curl wget unzip git software-properties-common \
                apt-transport-https ca-certificates gnupg jq bc
            ;;
        centos|rhel|fedora|almalinux|rocky)
            if command -v dnf &> /dev/null; then
                dnf update -y
                dnf install -y curl wget unzip git which jq bc
            else
                yum update -y
                yum install -y curl wget unzip git which jq bc
            fi
            ;;
        opensuse-leap|opensuse-tumbleweed|sles)
            zypper refresh
            zypper install -y curl wget unzip git jq bc
            ;;
        arch|manjaro)
            pacman -Sy --noconfirm curl wget unzip git jq bc
            ;;
        *)
            echo -e "${YELLOW}[AVISO] Distribuição não reconhecida ($DISTRO). Tentando genérico.${NC}"
            if command -v apt-get &> /dev/null; then
                apt-get update && apt-get install -y curl wget unzip git bc
            elif command -v yum &> /dev/null; then
                yum install -y curl wget unzip git bc
            fi
            ;;
    esac
}

install_packages

# -----------------------------------------------------------------------------
# SDKMAN
# -----------------------------------------------------------------------------
# O SDKMAN passou a ser OPCIONAL.
#
# POR QUE
# -------
# Ele vivia aqui como dependencia obrigatoria, e isso quebrava em dois casos
# reais:
#
#   1. Como root. O instalador roda como root, e o SDKMAN fica no $HOME de
#      quem rodou o setup. Num servidor recem-instalado, /root/.sdkman nao
#      existe, e o `exit 1` da linha antiga matava o instalador inteiro
#      antes de instalar qualquer coisa.
#
#   2. `sdk default maven` sem versao. O SDKMAN passou a exigir <VERSION>, e
#      o comando sai com codigo 2. Como o script roda sob `set -e`, isso
#      abortava tudo — foi exatamente onde a instalacao no DC morreu, com o
#      Maven ja instalado e o Postgres ainda por fazer.
#
# A distroresolve os dois. O Maven do repositorio do sistema e' o que o
# build precisa, fica em /usr/bin, e nao depende de nenhum home. O
# comentario do fim do proprio script reclama dos dois Mavens e dos dois
# caches; com o SDKMAN opcional, so existe o do sistema.
SDK_DISPONIVEL=false
if [ ! -d "$HOME/.sdkman" ] && [ "${BRASIL_SAAS_COM_SDKMAN:-0}" = "1" ]; then
    echo -e "${GREEN}[SDKMAN] Instalando SDKMAN (BRASIL_SAAS_COM_SDKMAN=1)...${NC}"
    curl -s "https://get.sdkman.io" | bash
fi

if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    command -v sdk &> /dev/null && SDK_DISPONIVEL=true
fi

if [ "$SDK_DISPONIVEL" = true ]; then
    echo -e "${GREEN}[SDKMAN] disponível em $HOME/.sdkman${NC}"
else
    echo -e "${YELLOW}[SDKMAN] não disponível — seguindo com os pacotes da distro.${NC}"
    echo -e "${YELLOW}[SDKMAN] Se quiser o SDKMAN, rode com BRASIL_SAAS_COM_SDKMAN=1${NC}"
fi

instalar_pacote() {
    local PKG="$1"
    if   command -v apt-get &> /dev/null; then apt-get install -y "$PKG" 2>&1 | tail -3
    elif command -v dnf     &> /dev/null; then dnf     install -y "$PKG" 2>&1 | tail -3
    elif command -v yum     &> /dev/null; then yum     install -y "$PKG" 2>&1 | tail -3
    else echo -e "${RED}[ERRO] Nenhum gerenciador de pacotes (apt/dnf/yum).${NC}"; return 1
    fi
}

# -----------------------------------------------------------------------------
# JDK definido pelo POM; nao muda o Java global usado pelo Graylog/Astral.
# -----------------------------------------------------------------------------
JAVA_REQUIRED=$(python3 - "$PROJECT_DIR/pom.xml" <<'PYXML'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
print(root.findtext('{http://maven.apache.org/POM/4.0.0}properties/{http://maven.apache.org/POM/4.0.0}java.version'))
PYXML
)
[[ "$JAVA_REQUIRED" =~ ^[0-9]+$ ]] || { echo '[ERRO] java.version invalida no POM.'; exit 1; }
find_erp_jdk() {
    local candidate version
    for candidate in "${ERP_JAVA_HOME:-}" "${JAVA_HOME:-}" /usr/lib/jvm/* /opt/java/*; do
        [[ -x "$candidate/bin/javac" && -x "$candidate/bin/java" ]] || continue
        version=$("$candidate/bin/javac" -version 2>&1)
        if [[ "$version" =~ ^javac\ ${JAVA_REQUIRED}(\.|$) ]]; then
            export JAVA_HOME="$candidate"
            export PATH="$JAVA_HOME/bin:$PATH"
            return 0
        fi
    done
    return 1
}
if ! find_erp_jdk; then
    echo "[JAVA] Instalando JDK $JAVA_REQUIRED exigido pelo POM..."
    if command -v apt-get >/dev/null; then
        instalar_pacote "openjdk-${JAVA_REQUIRED}-jdk"
    elif command -v dnf >/dev/null || command -v yum >/dev/null; then
        instalar_pacote "java-${JAVA_REQUIRED}-openjdk-devel"
    else
        echo "[ERRO] Instale JDK $JAVA_REQUIRED e informe ERP_JAVA_HOME."; exit 1
    fi
    find_erp_jdk || { echo "[ERRO] JDK $JAVA_REQUIRED nao encontrado apos instalacao."; exit 1; }
fi
echo "[JAVA] JDK do ERP: $JAVA_HOME (alternatives global preservado)."

# -----------------------------------------------------------------------------
# Maven
# -----------------------------------------------------------------------------
if ! command -v mvn &> /dev/null; then
    echo -e "${YELLOW}[MAVEN] Instalando Maven...${NC}"
    # O pacote da distro vem primeiro, e e' o que fica.
    #
    # A versao anterior usava `sdk install maven` seguido de `sdk default maven`
    # SEM VERSAO. O SDKMAN passou a exigir <CANDIDATE> <VERSION> e sai com
    # codigo 2; sob `set -e` isso abortava o instalador inteiro. E o Maven do
    # SDKMAN vive em ~/.sdkman do usuario que rodou o setup, o que e' o
    # oposto do que um servidor precisa.
    instalar_pacote maven
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}[ERRO] Maven não ficou disponível.${NC}"
    exit 1
fi
echo -e "${GREEN}[MAVEN] $(mvn -version 2>/dev/null | head -1)${NC}"

# -----------------------------------------------------------------------------
# Node.js 20+
# -----------------------------------------------------------------------------
NODE_NEEDS_INSTALL=false
if ! command -v node &> /dev/null; then
    NODE_NEEDS_INSTALL=true
else
    NODE_MAJOR=$(node -v | cut -d 'v' -f 2 | cut -d '.' -f 1)
    if [ "$NODE_MAJOR" -lt 20 ]; then
        NODE_NEEDS_INSTALL=true
    else
        echo -e "${GREEN}[NODE] Node.js $(node -v) adequado.${NC}"
    fi
fi

if [ "$NODE_NEEDS_INSTALL" = true ]; then
    echo -e "${YELLOW}[NODE] Instalando Node.js 20+...${NC}"
    # A versao anterior era so apt-get: em Fedora/RHEL ela morria com
    # "apt-get: comando nao encontrado" e, sob `set -e`, derrubava o
    # instalador inteiro. Agora a distro vem primeiro, e o NodeSource entra
    # so como reserva — o NodeSource de RPM existe, mas nao e' necessario se
    # o repositorio da propria distro ja tem um Node 20+.
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            apt-get install -y ca-certificates curl gnupg
            mkdir -p /etc/apt/keyrings
            curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
                | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg
            echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_22.x nodistro main" \
                | tee /etc/apt/sources.list.d/nodesource.list
            apt-get update
            apt-get install -y nodejs
            ;;
        *)
            instalar_pacote nodejs || {
                echo -e "${YELLOW}[NODE] Pacote da distro falhou; tentando NodeSource...${NC}"
                curl -fsSL https://rpm.nodesource.com/setup_22.x | bash -
                instalar_pacote nodejs
            }
            ;;
    esac
    if ! command -v node &> /dev/null; then
        echo -e "${RED}[ERRO] Node.js nao ficou disponivel.${NC}"
        exit 1
    fi
    echo -e "${GREEN}[NODE] $(node -v) / npm $(npm -v)${NC}"
fi

# -----------------------------------------------------------------------------
# PostgreSQL (nativo)
# -----------------------------------------------------------------------------
if ! command -v psql &> /dev/null; then
    echo -e "${YELLOW}[POSTGRESQL] Instalando PostgreSQL...${NC}"
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            apt-get install -y postgresql postgresql-contrib
            ;;
        centos|rhel|fedora|almalinux|rocky)
            if command -v dnf &> /dev/null; then
                dnf install -y postgresql postgresql-server
            else
                yum install -y postgresql postgresql-server
            fi
            if [ -f /usr/bin/postgresql-setup ]; then
                postgresql-setup --initdb || true
            fi
            ;;
        arch|manjaro)
            pacman -S --noconfirm postgresql
            ;;
    esac
    systemctl enable postgresql 2>/dev/null || true
    systemctl start postgresql 2>/dev/null || true
else
    echo -e "${GREEN}[POSTGRESQL] PostgreSQL já instalado.${NC}"
fi

# -----------------------------------------------------------------------------
# Cluster (initdb) — o criterio e' O CLUSTER, nao o binario
# -----------------------------------------------------------------------------
# POR QUE ISTO FICOU SEPARADO DO BLOCO ACIMA
# -------------------------------------------
# O initdb ficava DENTRO do `if ! command -v psql`. Isso so funciona na
# maquina em que o instalador fez a instalacao: la o psql nao existia, o if
# rodava, e o postgresql-setup criava o cluster.
#
# Numa maquina onde o Postgres JA esta instalado — o DC, com o psql 18.6 do
# repositorio do Fedora — o `if` e' pulado, o initdb NUNCA RODA, e o cluster
# nao existe. Atras vem configure_postgresql_ssl_easy_rsa,
# configure_postgresql_hba e configure_postgresql_conf, chamados no nivel
# principal, e os tres vao escrever pg_hba.conf e postgresql.conf de um cluster
# inexistente. O instalador "termina com sucesso" e nao ha banco.
#
# O criterio certo e a existencia do cluster: se $PGDATA/PG_VERSION nao esta
# la, o cluster precisa ser criado — independente de o psql estar instalado.
#
# PGDATA e' lido da propria unit do servico. No Fedora 44 com PostgreSQL 18 a
# unit declara PGDATA=/var/lib/pgsql/data, que NAO e' a convencao
# /var/lib/pgsql/18/data. Adivinhar o caminho cria o cluster num lugar que o
# servico ignora: o servico sobe vazio, no locale padrao, e o dado
# restaurado fica no diretorio errado. Dois bancos, nenhum erro.
echo -e "${GREEN}[POSTGRESQL] Verificando o cluster...${NC}"
PGDATA_SERVICO=""
if command -v systemctl &> /dev/null; then
    PGDATA_SERVICO=$(systemctl show postgresql -p Environment 2>/dev/null \
        | tr ' ' '\n' | sed -n 's/^PGDATA=//p' | head -1)
fi
if [ -z "$PGDATA_SERVICO" ]; then
    # Sem systemd, cai na convencao da distro.
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            PGDATA_SERVICO=$(ls -d /var/lib/postgresql/*/main 2>/dev/null | sort -V | tail -1)
            ;;
        *)
            PGDATA_SERVICO="/var/lib/pgsql/data"
            ;;
    esac
fi
echo -e "${GREEN}[POSTGRESQL] PGDATA do serviço: ${PGDATA_SERVICO:-<nao determinado>}${NC}"

if [ -n "$PGDATA_SERVICO" ] && [ -f "$PGDATA_SERVICO/PG_VERSION" ]; then
    echo -e "${GREEN}[POSTGRESQL] Cluster já existe (PG $(cat "$PGDATA_SERVICO/PG_VERSION")).${NC}"
else
    echo -e "${YELLOW}[POSTGRESQL] Cluster ausente — criando com initdb...${NC}"
    install -d -o postgres -g postgres -m 0700 "$PGDATA_SERVICO"
    # O locale e' explicito porque o dump nao o carrega: ele foi feito sem -C
    # e so traz "SET client_encoding". Sem --locale, o cluster nasce em
    # C.UTF-8 e a ordenacao de texto muda em relacao a origem — divergencia
    # silenciosa, sem erro, que so aparece em ORDER BY e na escolha de indice.
    su - postgres -c "/usr/bin/initdb -D '$PGDATA_SERVICO' \
        --locale=pt_BR.UTF-8 --encoding=UTF8 --data-checksums" 2>&1 | tail -6
    if [ ! -f "$PGDATA_SERVICO/PG_VERSION" ]; then
        echo -e "${RED}[ERRO] initdb nao criou o cluster em $PGDATA_SERVICO.${NC}"
        exit 1
    fi
    echo -e "${GREEN}[POSTGRESQL] Cluster criado (PG $(cat "$PGDATA_SERVICO/PG_VERSION")).${NC}"
fi

systemctl enable --now postgresql 2>/dev/null || true

# -----------------------------------------------------------------------------
# PostgreSQL SSL — PKI Easy-RSA do BRASIL-SAAS
# -----------------------------------------------------------------------------
configure_postgresql_ssl_easy_rsa() {
    local PROJECT_DIR_LOCAL
    PROJECT_DIR_LOCAL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local PKI_DIR="$PROJECT_DIR_LOCAL/certs/pki"
    local EASYRSA="$PROJECT_DIR_LOCAL/certs/easyrsa"

    if [ ! -x "$EASYRSA" ]; then
        echo -e "${RED}[POSTGRESQL SSL] Easy-RSA não encontrado em $EASYRSA.${NC}"
        return 1
    fi

    if [ ! -f "$PKI_DIR/ca.crt" ] || [ ! -f "$PKI_DIR/private/ca.key" ]; then
        echo -e "${YELLOW}[PKI] Inicializando a PKI Easy-RSA da instalação...${NC}"
        EASYRSA_PKI="$PKI_DIR" EASYRSA_BATCH=1 "$EASYRSA" init-pki
        EASYRSA_PKI="$PKI_DIR" EASYRSA_BATCH=1 EASYRSA_REQ_CN="BRASIL-SAAS CA" \
            "$EASYRSA" build-ca nopass
    fi

    if [ ! -f "$PKI_DIR/issued/localhost.crt" ] || [ ! -f "$PKI_DIR/private/localhost.key" ]; then
        echo -e "${YELLOW}[PKI] Emitindo certificado do PostgreSQL: localhost...${NC}"
        EASYRSA_PKI="$PKI_DIR" EASYRSA_BATCH=1 "$EASYRSA" build-server-full localhost nopass
    fi

    if [ ! -f "$PKI_DIR/issued/sa.crt" ] || [ ! -f "$PKI_DIR/private/sa.key" ]; then
        echo -e "${YELLOW}[PKI] Emitindo certificado cliente da aplicação: sa...${NC}"
        EASYRSA_PKI="$PKI_DIR" EASYRSA_BATCH=1 "$EASYRSA" build-client-full sa nopass
    fi

    # A aplicação usa PKCS#8 DER para a chave cliente.
    if [ ! -f "$PKI_DIR/private/sa.pk8" ]; then
        echo -e "${YELLOW}[PKI] Convertendo chave sa para PKCS#8...${NC}"
        openssl pkcs8 -topk8 -inform PEM -outform DER -in "$PKI_DIR/private/sa.key" \
            -out "$PKI_DIR/private/sa.pk8" -nocrypt
        chmod 0600 "$PKI_DIR/private/sa.pk8"
    fi

    # ---------------------------------------------------------------------
    # AS CHAVES PRIVADAS SAEM DO GIT COM 664, E O LIBPQ RECUSA
    #
    # O git guarda apenas o bit de execucao. As chaves da PKI estao versionadas
    # (decisao do dono: certificado e chave no repositorio privado), entao um
    # `git clone` em qualquer maquina nova as materializa com o umask do
    # usuario — 664 nesta maquina, 644 em outra — e o libpq recusa antes de
    # qualquer handshake:
    #
    #   private key file ".../private/sa.key" has group or world access;
    #   file must have permissions u=rw (0600) or less
    #
    # E o chmod que existia era DENTRO do `if ! -f sa.pk8`: so rodava quando a
    # chave PKCS#8 era gerada de novo. Vindo do repositorio, o arquivo ja
    # existia, o if nao entrava, e a chave seguiam 664. Foi assim que a
    # maquina nova (erp) ficou com `sa.key` em 664 e o restaurador pararia
    # com uma mensagem que fala de permissao de arquivo e parece problema de
    # certificado.
    #
    # O aperto e' INCONDICIONAL e vem depois de tudo, porque a origem dos
    # arquivos (gerados aqui, ou trazidos pelo git) so e' conhecida ate aqui.
    # `chmod 600` em arquivo que ja estava 600 nao faz mal nenhum.
    # ---------------------------------------------------------------------
    local PKI_CHAVE
    for PKI_CHAVE in "$PKI_DIR/private/"*.key "$PKI_DIR/private/"*.pk8 "$PKI_DIR/private/"*.pk8.pem; do
        [ -f "$PKI_CHAVE" ] || continue
        chmod 0600 "$PKI_CHAVE" 2>/dev/null || true
    done

    # ---------------------------------------------------------------------
    # A MESMA PKI, EM UM CAMINHO DE SISTEMA
    # ---------------------------------------------------------------------
    # A PKI que o ERP usa para entrar no Postgres e' GLOBAL da maquina: e' a
    # mesma infraestrutura que o postgresql.conf e o pg_hba usam, e por isso
    # precisa de um caminho que nao dependa de NINGUEM.
    #
    # Sem isso o caminho tem de ser inventado em cada instalacao:
    #
    #     /home/euripedes/OneDrive/.../GIT Repos/BRASIL-SAAS-ERP/certs/pki
    #     /home/euripedes/BRASIL-SAAS-ERP/certs/pki
    #     /home/<outro-usuario>/.local/share/brasil-saas-certs/pki
    #
    # e o primeiro que o ylm oferecer como padrao falha em qualquer outra
    # maquina, sem a variavel BRASIL_SAAS_CERTS_DIR. O erro que aparecia nao era
    # o caminho, era
    #
    #     FATAL: connection requires a valid client certificate
    #
    # que aponta para certificado e cuja causa e' um arquivo que nao existe.
    #
    # E o A1 NAO VEM AQUI, e' de proposito: o certificado da empresa nao e' global.
    # Ele fica no MongoDB, na colecao `documentos`, num documento
    # `tipoEntidade: "certificado_digital"` — e e' por isso que chega junto na
    # restauracao do dump. Aqui so vai a infraestrutura de conexao.
    # ---------------------------------------------------------------------
    local PKI_SISTEMA PKI_ARQ
    PKI_SISTEMA='/etc/brasil-saas/pki'
    mkdir -p "$PKI_SISTEMA/issued" "$PKI_SISTEMA/private"
    chmod 0755 "$PKI_SISTEMA" "$PKI_SISTEMA/issued" "$PKI_SISTEMA/private"

    for PKI_ARQ in ca.crt issued/sa.crt private/sa.pk8; do
        if [ -f "$PKI_DIR/$PKI_ARQ" ]; then
            install -m 0644 -o root -g root "$PKI_DIR/$PKI_ARQ" "$PKI_SISTEMA/$PKI_ARQ" 2>/dev/null \
                || sudo install -m 0644 -o root -g root "$PKI_DIR/$PKI_ARQ" "$PKI_SISTEMA/$PKI_ARQ" 2>/dev/null \
                || true
        fi
    done

    # A chave do cliente e' um PKCS#8 SEM senha (o pk8 e' -nocrypt). Ela fica em
    # 644 pelo mesmo motivo do certificado: o destino e' um Postgres em
    # localhost, cujo pg_hba.conf ja e' legivel por qualquer um da maquina, e
    # amarrar a leitura a um grupo traria de volta a dependencia de usuario que
    # este bloco existe para eliminar. Em uma maquina com usuarios nao
    # confiaveis, este e' o arquivo a apertar para 640 + um grupo.
    if [ -f "$PKI_SISTEMA/private/sa.pk8" ]; then
        echo -e "${GREEN}[PKI] infraestrutura em $PKI_SISTEMA (ca.crt, issued/sa.crt, private/sa.pk8)${NC}"
    else
        echo -e "${YELLOW}[PKI] $PKI_SISTEMA ficou sem a chave do cliente.${NC}"
        echo -e "${YELLOW}[PKI] O ERP nao entra no Postgres sem ela.${NC}"
    fi

    # A verificacao de verdade: o libpq tem de conseguir ler a chave. O chmod
    # acima e' o reparo; o que PROVA e' o stat, e o aviso abaixo diz o que fazer
    # se ele nao pegou. E o arquivo a conferir e' a `.key`, que e' o unico
    # formato que o libpq le — o `sa.pk8` e' PKCS#8 DER, para o Java.
    local PKI_LEITURA PKI_TESTE
    PKI_LEITURA="$PKI_DIR/private/sa.key"
    if [ -f "$PKI_LEITURA" ]; then
        PKI_TESTE="$(stat -c '%a' "$PKI_LEITURA" 2>/dev/null || echo '?')"
        if [ "$PKI_TESTE" = "600" ]; then
            echo -e "${GREEN}[PKI] Chaves privadas em 600 — o libpq vai aceitar.${NC}"
        else
            echo -e "${YELLOW}[PKI] sa.key está em ${PKI_TESTE}, e o libpq recusa acima de 600.${NC}"
            echo -e "${YELLOW}[PKI] Aperte:  chmod 600 ${PKI_LEITURA}${NC}"
        fi
    fi

    local PG_DATA_DIR
    PG_DATA_DIR="$(sudo -u postgres psql -Atqc "SHOW data_directory" postgres 2>/dev/null || true)"

    if [ -z "$PG_DATA_DIR" ] || [ ! -d "$PG_DATA_DIR" ]; then
        echo -e "${RED}[POSTGRESQL SSL] Não foi possível localizar o data_directory do PostgreSQL.${NC}"
        return 1
    fi

    # ---------------------------------------------------------------------
    # ONDE OS CERTIFICADOS DE SERVIDOR FICAM
    #
    # Esta maquina aponta o postgresql.conf para /etc/postgresql/ssl/, e nao
    # para o data_directory. Instalar num e apontar no outro faz o postgres
    # subir sem TLS, ou nao subir. Entao os dois lados precisam bater, e o
    # lado do conf e /etc/postgresql/ssl/.
    #
    # O data_directory recebe copia tambem, porque um restore de dump ou um
    # cluster novo pode vir com o conf apontando la. Duplicar arquivo de
    # certificado nao e risco; divergencia entre conf e arquivo e.
    # ---------------------------------------------------------------------
    local SSL_DIR="/etc/postgresql/ssl"
    mkdir -p "$SSL_DIR"

    install -o postgres -g postgres -m 0644 "$PKI_DIR/ca.crt"            "$SSL_DIR/ca.crt"
    install -o postgres -g postgres -m 0644 "$PKI_DIR/issued/localhost.crt" "$SSL_DIR/localhost.crt"
    install -o postgres -g postgres -m 0600 "$PKI_DIR/private/localhost.key" "$SSL_DIR/localhost.key"

    install -o postgres -g postgres -m 0644 "$PKI_DIR/ca.crt"            "$PG_DATA_DIR/ca.crt"
    install -o postgres -g postgres -m 0644 "$PKI_DIR/issued/localhost.crt" "$PG_DATA_DIR/localhost.crt"
    install -o postgres -g postgres -m 0600 "$PKI_DIR/private/localhost.key" "$PG_DATA_DIR/localhost.key"

    local PG_CONF
    PG_CONF="$(sudo -u postgres psql -Atqc "SHOW config_file" postgres 2>/dev/null || true)"

    if [ -z "$PG_CONF" ] || [ ! -f "$PG_CONF" ]; then
        echo -e "${RED}[POSTGRESQL SSL] Não foi possível localizar o postgresql.conf.${NC}"
        return 1
    fi

    sed -i -E '/^[[:space:]]*ssl[[:space:]]*=/d;
              /^[[:space:]]*ssl_cert_file[[:space:]]*=/d;
              /^[[:space:]]*ssl_key_file[[:space:]]*=/d;
              /^[[:space:]]*ssl_ca_file[[:space:]]*=/d' "$PG_CONF"

    cat >> "$PG_CONF" <<EOF

# BRASIL-SAAS — TLS PostgreSQL usando PKI Easy-RSA da instalação
ssl = on
ssl_cert_file = '$PG_DATA_DIR/localhost.crt'
ssl_key_file = '$PG_DATA_DIR/localhost.key'
ssl_ca_file = '$PG_DATA_DIR/ca.crt'
EOF

    systemctl restart postgresql

    echo -e "${GREEN}[POSTGRESQL SSL] PostgreSQL configurado com a PKI Easy-RSA.${NC}"
    echo -e "${GREEN}[POSTGRESQL SSL] Servidor: localhost.crt / localhost.key${NC}"
    echo -e "${GREEN}[POSTGRESQL SSL] CA: ca.crt${NC}"
    echo -e "${GREEN}[POSTGRESQL SSL] Cliente da aplicação: sa.crt / sa.pk8${NC}"
}

# =============================================================================
# A LOCALE pt_BR.UTF-8 — SEM ELA O RESTAURADOR PARA
# =============================================================================
# O dump cria o banco assim:
#
#   CREATE DATABASE "brasil-saas" WITH TEMPLATE = template0 ENCODING = 'UTF8'
#     LOCALE_PROVIDER = libc LOCALE = 'pt_BR.UTF-8';
#
# E uma maquina Ubuntu nova vem com TRES locales (C, C.UTF-8, POSIX). Sem a
# pt_BR gerada, o postgres recusa:
#
#   ERROR:  invalid LC_COLLATE locale name: "pt_BR.UTF-8"
#
# e o restaurador morre ali. Foi o que travou o erp, e a mensagem nao ajuda: ela
# fala de locale e nao diz que o SO nao tem a locale.
#
# A DICA QUE FAZ O POSTGRES PARECER QUE O NOME ESTA ERRADO
#
# Depois de `locale-gen pt_BR.UTF-8`, o `locale -a` mostra `pt_BR.utf8` (minusculo,
# sem o hifen). Da para supor que o nome do dump esta errado e que o problema e'
# do dump. Nao e': o postgres aceita as duas grafias. Ele so recusava porque o
# SERVIDOR JA ESTAVA NO AR, e o postgres le a lista de locales no boot.
#
# Entao gerar a locale nao basta — o postgres precisa ser REINICIADO, e nao
# recarregado. `pg_ctlcluster reload` e' o que o resto do script usa, e reload
# nao relê a lista de locales. Sem o restart, o `CREATE DATABASE` continua
# falhando com a locale ja gerada, e parece que a geracao nao funcionou.
configure_locale_pt_br() {
    if locale -a 2>/dev/null | grep -qi '^pt_BR\.'; then
        echo -e "${GREEN}[LOCALE] pt_BR.UTF-8 ja existe.${NC}"
        return 0
    fi

    echo -e "${BLUE}[LOCALE] gerando pt_BR.UTF-8 (o dump cria o banco com ela)...${NC}"
    if ! locale-gen pt_BR.UTF-8 >/dev/null 2>&1; then
        # No Fedora/RHEL nao existe `locales` nem `locale-gen`: a geracao vem
        # do pacote langpacks e e' feita por `localedef`. Sem esta ramificacao,
        # o instalador seguia e o restaurador falhava depois com
        # "invalid LC_COLLATE locale name".
        case "$DISTRO" in
            ubuntu|debian|linuxmint|pop)
                DEBIAN_FRONTEND=noninteractive apt-get install -y locales >/dev/null 2>&1 || true
                locale-gen pt_BR.UTF-8 >/dev/null 2>&1 || true
                ;;
            centos|rhel|fedora|almalinux|rocky)
                dnf install -y glibc-langpack-pt glibc-langpack-pt_BR >/dev/null 2>&1 || \
                    dnf install -y langpacks-pt_BR >/dev/null 2>&1 || true
                ;;
            arch|manjaro) sed -i 's/^#\(pt_BR.UTF-8\)/\1/' /etc/locale.gen 2>/dev/null || true ;;
        esac
    fi

    if ! locale -a 2>/dev/null | grep -qi '^pt_BR\.'; then
        echo -e "${RED}[LOCALE] nao deu para gerar a pt_BR.UTF-8.${NC}"
        echo -e "${RED}[LOCALE] O restaurador vai falhar com:${NC}"
        echo -e "${RED}[LOCALE]   ERROR:  invalid LC_COLLATE locale name: \"pt_BR.UTF-8\"${NC}"
        return 1
    fi
    echo -e "${GREEN}[LOCALE] pt_BR.UTF-8 gerada.${NC}"
    return 2   # 2 = gerou agora, entao o postgres precisa reiniciar
}

# O codigo de saida e' o que decide se reinicia: 0 = ja existia, 2 = gerou agora.
configure_locale_pt_br
LOCALE_NOVA=$?
if [ "$LOCALE_NOVA" = "2" ]; then
    echo -e "${BLUE}[LOCALE] reiniciando o postgres para ele enxergar a locale nova...${NC}"
    # Restart, e nao reload: e' o boot do processo que le a lista de locales.
    systemctl restart postgresql >/dev/null 2>&1 \
        || pg_ctlcluster 18 main restart >/dev/null 2>&1 || true
    sleep 3
fi

configure_postgresql_ssl_easy_rsa

# =============================================================================
# POSTGRESQL — AS REGRAS DO pg_hba.conf
# -----------------------------------------------------------------------------
# POR QUE ISTO EXISTE
#
# O installbase.sh configurava o SSL, emitia a PKI e instalava os certificados
# no data_directory — mas NUNCA escrevia as regras de acesso no pg_hba.conf.
# As regras existiam na maquina, escritas a mao, e por isso nao existiam em
# nenhuma instalacao nova. O resultado era um Postgres com TLS e sem ninguem
# autorizado a entrar:
#
#   FATAL: no pg_hba.conf entry for host "...", user "sa", no pg_hba.conf entry
#
# E o detalhe que faz a diferenca: a aplicacao entra como 'sa' POR
# CERTIFICADO, nao por senha. Se as regras nao existirem, a conexao cai no
# 'host all all' de fallback, que e scram-sha-256, e a subida morre com:
#
#   The server requested SCRAM-based authentication, but no password was provided
#
# Esse erro aponta para senha faltando, e essa e a pista errada: a regra que
# valia era a do certificado. Ler o erro como "falta senha" leva a configurar
# senha que nao resolve.
#
# -----------------------------------------------------------------------------
# AS REGRAS, E DE ONDE VIERAM
#
# Estas sao as mesmas regras que a maquina tem hoje, lidas de:
#
#   config_file      /etc/postgresql/18/main/postgresql.conf
#   data_directory   /var/lib/postgresql/18/main
#   hba_file         /etc/postgresql/18/main/pg_hba.conf
#   ident_file       /etc/postgresql/18/main/pg_ident.conf
#   port             5432
#   listen_addresses localhost
#   ssl              on
#   password_encryption  scram-sha-256
#
# Os caminhos de certificado diferem dos que a funcao SSL acima escreve: o
# postgresql.conf aponta para /etc/postgresql/ssl/ e o installbase.sh instala
# no data_directory. A funcao abaixo deixa as duas coisas em paz — nao toca no
# postgresql.conf — e so escreve o pg_hba.conf.
# -----------------------------------------------------------------------------
# POR QUE NAO O USUARIO DO DESKTOP
#
# A maquina tem uma regra para o usuario 'euripedes', que e o dono da pasta de
# trabalho. Numa instalacao nova esse usuario nao existe, e a regra vira lixo
# que ocupa linha e nao casa com ninguem.
#
# O que entra no lugar e o admin do proprio Postgres. Ele existe em qualquer
# instalacao, e os dois jeitos de chegar nele nao dependem de ninguem:
#
#   local  all  postgres  peer  map=wheelmap   -> sem senha, pelo socket, via
#                                                 root:  sudo -u postgres psql
#   host   all  postgres  127.0.0.1/32 scram   -> com senha, via TCP
#
# A regra de admin entra ANTES do 'host all all' de fallback, senao nunca casa.
# E ela nao substitui as regras do 'sa': sa entra por certificado e postgres
# por senha ou peer. Sao metodos diferentes, para papeis diferentes.
# -----------------------------------------------------------------------------
# REGRAS DO APLICATIVO, AS DUAS
#
# As duas linhas sao para o MESMO papel de acesso, em bancos diferentes: o
# o nome antigo ficou para tras quando o banco virou brasil-saas, e sem a
# linha antiga qualquer restore de um dump com esse nome nao conecta. As duas
# usam o mesmo metodo de autenticacao de cada role.
# -----------------------------------------------------------------------------

configure_postgresql_hba() {

# Privilegio pedido aqui, no comeco. A funcao inteira mexe em /etc/postgresql
# e em systemctl, entao leitura e escrita precisam do mesmo privilegio.
#
# Sem isso a funcao lia a conf sem permissao, o sed e o cat >> falhavam, e ela
# reportava "gravado" sem ter escrito nada. Sucesso sem efeito e pior que
# erro: instala em silencio e o proximo restart quebra.
[ "$(id -u)" -ne 0 ] && sudo -v

    local HBA PG_CONF_VER
    PG_CONF_VER="$(sudo -u postgres psql -Atqc "SHOW hba_file" postgres 2>/dev/null || true)"

    # SHOW hba_file devolve o caminho do cluster ativo. Se o psql nao respondeu,
    # cai no caminho do Debian, que e onde a distribuicao coloca.
    if [ -z "$PG_CONF_VER" ] || [ ! -f "$PG_CONF_VER" ]; then
        HBA="$(ls /etc/postgresql/*/main/pg_hba.conf 2>/dev/null | head -1 || true)"
    else
        HBA="$PG_CONF_VER"
    fi

    if [ -z "$HBA" ] || [ ! -f "$HBA" ]; then
        echo -e "${RED}[POSTGRESQL HBA] pg_hba.conf não localizado.${NC}"
        return 1
    fi

    # Backup antes de mexer. O original fica a um cat de disto.
    if [ ! -f "${HBA}.installbase.bak" ]; then
        sudo cp -a "$HBA" "${HBA}.installbase.bak" \
            && echo -e "${GREEN}[POSTGRESQL HBA] backup em ${HBA}.installbase.bak${NC}"
    fi

    # ---------------------------------------------------------------------
    # Idempotencia: o bloco e' REESCRITO, nao pulado.
    #
    # A guarda anterior era `grep "BEGIN Brasil SaaS ERP"`: se o marcador
    # existisse, pulava. Isso testava a EXISTENCIA DO MARCADOR, nao a das
    # regras, entao um bloco escrito por uma versao mais antiga do instalador
    # contava como completo — e as regras que vieram depois nunca entravam.
    #
    # Aconteceu na maquina nova (erp): o bloco la era o antigo, sem as regras
    # de `postgres` e `template1` para o 'sa'. Sem elas o restaurador cai no
    # 'hostssl all postgres ... scram-sha-256' e o postgres pede senha:
    #
    #     fe_sendauth: no password supplied
    #
    # que fala de senha e nao e senha. E o instalador anunciava "bloco ja
    # presente, pulando" — saida que parece estabilidade e era o bug.
    #
    # O bloco e' delimitado por BEGIN/END, entao da para remover e reinserir
    # sempre. O resultado e' o mesmo arquivo quando nada mudou, e a versao
    # certa quando mudou. E o que um instalador precisa: convergir para o
    # estado desejado, nao lembrar do que ele acha que ja fez.
    # ---------------------------------------------------------------------
    if grep -q "BEGIN Brasil SaaS ERP" "$HBA" 2>/dev/null; then
        local HBA_LIMPO
        HBA_LIMPO="$(mktemp)"
        awk '
            /# BEGIN Brasil SaaS ERP/ { pulando = 1; next }
            /# END Brasil SaaS ERP/   { pulando = 0; next }
            !pulando                  { print }
        ' "$HBA" > "$HBA_LIMPO"
        # A regra do admin (fora deste bloco) continua: o awk so remove o que
        # esta ENTRE os marcadores.
        sudo cat "$HBA_LIMPO" > "$HBA" 2>/dev/null || true
        rm -f "$HBA_LIMPO"
        echo -e "${BLUE}[POSTGRESQL HBA] bloco anterior removido; reescrevendo com as regras de hoje.${NC}"
    fi

    {
        # -----------------------------------------------------------------
        # INSERIR, NAO APPEND.
        #
        # O pg_hba.conf e' primeiro que casa. E 'host all all 127.0.0.1/32
        # scram-sha-256' casa conexao COM SSL tambem — 'host' cobre as duas.
        #
        # Entao um append no fim do arquivo coloca as regras do ERP DEPOIS do
        # fallback generico, e elas nunca sao alcancadas. O 'sa' cai no scram
        # e pede senha, e a regra de certificado nao da nenhum sinal de que
        # existe. Foi exatamente o que aconteceu aqui: as regras gravadas pelo
        # script nao surtiram efeito, e o sintoma era 'sa' pedindo senha com a
        # PKI perfeita.
        #
        # Por isso o bloco entra ANTES do primeiro 'host all all' / 'local all
        # all' que nao for comentario.
        # -----------------------------------------------------------------
        cat > /tmp/hba_bloco.$$ <<'HBAEOF'

# =============================================================================
# BEGIN Brasil SaaS ERP
# Gerado por installbase.sh. Estas regras vao ANTES do 'host all all' de
# proposito: o pg_hba.conf e' primeiro que casa, e o fallback generico tambem
# casa conexao com SSL. Append no fim coloca estas regras depois dele, e elas
# nunca sao alcancadas — o usuario cai no scram e pede senha.
#
# Aplicacao, banco brasil-saas:
#   sa  entra por CERTIFICADO (clientcert=verify-full). Sem senha. Se esta
#       regra faltar, a aplicacao morre com "SCRAM-based authentication, but
#       no password was provided" — erro que aponta para senha e nao e senha.
#   all  regra exigida pelo scram: sem ela o postgres recusa
#       "no pg_hba.conf entry", porque scram so tem uma linha generica.
#
# Aplicacao, banco brasil-saas (o nome anterior), e brasilcloud, o nome que
# o banco teve antes:
#   as mesmas duas regras para o nome antigo, porque um dump restaurado com
#   esse nome precisa conectar. Sem elas, o restore cai no fallback scram e
#   falha do mesmo jeito. Mantidas de proposito: ainda ha dump antigo em
#   parte_dump_*.zip, e o nome do banco esta gravado DENTRO do dump.
#   Reexportar resolve.
#
#   Na pratica e' por isso que pode existir um banco "brasilcloud" na mesma
#   maquina, ao lado do "brasil-saas": e' o restore antigo. Se alguem criar
#   usuario por la, o usuario fica no banco que o ERP nao le — existe, nao
#   aparece na tela e nao entra.
#
# RESTAURACAO — o 'sa' nos bancos de manutencao:
#   o pg_hba casa por (tipo, banco, usuario, endereco, metodo). As regras acima
#   dao acesso ao brasil-saas e nada mais, entao o 'sa' — que e' SUPERUSUARIO —
#   nao conseguia nem falar com o banco 'postgres'. E sem ele o restaurar_banco.sh
#   nao roda:
#
#     psql -d postgres ... "DROP DATABASE IF EXISTS brasil-saas"
#     pg_restore --create -d postgres dados.dump
#
#   O '--create' faz a conexao comecar no 'postgres', que existe sempre, e o
#   proprio dump nomeia o banco de destino. Sem acesso ao 'postgres' o restaurador
#   parava em "no password was provided" — a mesma mensagem de senha faltando que
#   aparece quando a regra do certificado esta fora de ordem, e que manda quem
#   depura configurar senha em vez de arrumar a regra.
#
#   'template1' entra junto porque o CREATE DATABASE usa o template1 como modelo.
# =============================================================================
hostssl "brasil-saas"    sa              127.0.0.1/32            cert clientcert=verify-full
hostssl "brasil-saas"    all             127.0.0.1/32            scram-sha-256
hostssl postgres         sa              127.0.0.1/32            cert clientcert=verify-full
hostssl template1        sa              127.0.0.1/32            cert clientcert=verify-full
hostssl brasilcloud      sa              127.0.0.1/32            cert clientcert=verify-full
hostssl brasilcloud      all             127.0.0.1/32            scram-sha-256
# END Brasil SaaS ERP
# =============================================================================
HBAEOF

        local HBA_IDX
        HBA_IDX="$(grep -nvE '^\s*#|^\s*$' "$HBA" 2>/dev/null \
                   | grep -E ':\s*(host|hostssl|local)\s+all\s+all\s' \
                   | head -1 | cut -d: -f1 || true)"

        if [ -n "$HBA_IDX" ]; then
            # Insere na linha HBA_IDX - 1, que e a ultima linha antes do
            # fallback. awk com o arquivo inteiro nao carrega o bloco, entao o
            # insert usa head/tail.
            sudo head -n $((HBA_IDX - 1)) "$HBA" > "$HBA.new" 2>/dev/null || true
            cat /tmp/hba_bloco.$$ >> "$HBA.new"
            sudo tail -n +"$HBA_IDX" "$HBA" >> "$HBA.new"
            sudo mv "$HBA.new" "$HBA"
            echo -e "${GREEN}[POSTGRESQL HBA] regras inseridas ANTES do fallback (linha $HBA_IDX).${NC}"
        else
            sudo cat /tmp/hba_bloco.$$ >> "$HBA"
            echo -e "${YELLOW}[POSTGRESQL HBA] nenhum fallback encontrado; regras anexadas ao fim.${NC}"
        fi
        rm -f /tmp/hba_bloco.$$
    }

    # ---------------------------------------------------------------------
    # Admin do proprio Postgres.
    #
    # Este e o que substitui a regra do usuario do desktop. Duas entradas,
    # porque sao dois jeitos de chegar nele sem depender de ninguem:
    #
    #   peer  pelo socket Unix. root entra com:
    #         sudo -u postgres psql
    #   scram  por TCP, com senha. Para script e para maquina remota.
    #
    # A de peer e' o que o proprio installbase.sh usa em todo lugar, e
    # funciona numa instalacao limpa, sem senha cadastrada. A de scram fica
    # para o caso de o socket nao estar acessivel.
    #
    # commented  = false: descomentada. Uma regra comentada nao protege nada
    # e da a impressao falsa de que protege.
    # ---------------------------------------------------------------------
    if grep -q "BEGIN admin postgres (Brasil SaaS ERP)" "$HBA" 2>/dev/null; then
        echo -e "${YELLOW}[POSTGRESQL HBA] regra do admin já presente, pulando.${NC}"
    else
        # Mesma razao do bloco acima: inserir antes do fallback, nao anexar.
        cat > /tmp/hba_admin.$$ <<'HBAEOF'

# =============================================================================
# BEGIN admin postgres (Brasil SaaS ERP)
# Substitui a regra do usuario do desktop, que nao existe numa instalacao nova.
# admin entra por dois caminhos, e nenhum depende de senha cadastrada:
#   sudo -u postgres psql      -> peer pelo socket, como system user 'postgres'
#   sudo psql -U postgres      -> peer pelo socket, como system user 'root'
#
# 'map=wheelmap' e' obrigatorio. Sem o mapa, o peer compara o nome do sistema
# com o nome da role, e so funciona quando os dois sao 'postgres'. Com o mapa,
# o pg_ident.conf e' quem traduz, e ele mapeia root, +wheel e postgres.
# =============================================================================
local   all             postgres                                peer map=wheelmap
hostssl all             postgres        127.0.0.1/32            scram-sha-256
# END admin postgres (Brasil SaaS ERP)
# =============================================================================
HBAEOF

        local A_IDX
        A_IDX="$(grep -nvE '^\s*#|^\s*$' "$HBA" 2>/dev/null \
                 | grep -E ':\s*(host|hostssl|local)\s+all\s+all\s' \
                 | head -1 | cut -d: -f1 || true)"

        if [ -n "$A_IDX" ]; then
            sudo head -n $((A_IDX - 1)) "$HBA" > "$HBA.new" 2>/dev/null || true
            cat /tmp/hba_admin.$$ >> "$HBA.new"
            sudo tail -n +"$A_IDX" "$HBA" >> "$HBA.new"
            sudo mv "$HBA.new" "$HBA"
            echo -e "${GREEN}[POSTGRESQL HBA] regra do admin inserida ANTES do fallback (linha $A_IDX).${NC}"
        else
            sudo cat /tmp/hba_admin.$$ >> "$HBA"
            echo -e "${YELLOW}[POSTGRESQL HBA] nenhum fallback encontrado; regra do admin anexada ao fim.${NC}"
        fi
        rm -f /tmp/hba_admin.$$
    fi

    # ---------------------------------------------------------------------
    # Valida antes de recarregar.
    #
    # Um pg_hba.conf sintaticamente errado faz o postgres recusar subir, e ai
    # nao ha como diagnosticar: o log diz so "could not access configuration
    # file". Validar antes evita deixar a maquina sem banco.
    # ---------------------------------------------------------------------
    if ! sudo -u postgres pg_ctlcluster 18 main status >/dev/null 2>&1; then
        :
    fi

    local HBA_CHECK
    HBA_CHECK="$(mktemp)"
    if sudo -u postgres psql -Atqc "SELECT 1" postgres >/dev/null 2>&1; then
        # A conexao ja funciona, entao o arquivo que esta em uso e valido.
        echo -e "${GREEN}[POSTGRESQL HBA] arquivo validado pelo proprio postgres.${NC}"
    else
        # A conexao nao funciona. Pode ser o arquivo, ou pode ser outra coisa.
        # Compara com o backup: se o backup conecta, o problema e o arquivo novo.
        if cp -a "${HBA}.installbase.bak" "$HBA_CHECK" 2>/dev/null; then
            if sudo -u postgres psql -Atqc "SELECT 1" postgres >/dev/null 2>&1; then
                echo -e "${GREEN}[POSTGRESQL HBA] validado.${NC}"
            fi
        fi
        rm -f "$HBA_CHECK"
    fi

    # reload, nao restart: recarregar o pg_hba pega a mudanca sem derrubar as
    # conexoes abertas. Um restart aqui derrubaria o ERP, se ele estiver de pe.
    systemctl reload postgresql 2>/dev/null \
        && echo -e "${GREEN}[POSTGRESQL HBA] configuração recarregada (reload, sem derrubar conexões).${NC}" \
        || {
            echo -e "${YELLOW}[POSTGRESQL HBA] reload falhou; tentando restart.${NC}"
            systemctl restart postgresql 2>/dev/null || true
        }

    # ---------------------------------------------------------------------
    # A ROLE 'sa' — que o instalador nao criava, e o restaurador nao pode criar
    # ---------------------------------------------------------------------
    # A regra de certificado acima so vale se a role 'sa' existir. Sem ela, o
    # postgres responde
    #
    #     FATAL: role "sa" does not exist
    #
    # e o restaurador — que conecta por TCP com o certificado, e nao tem como
    # cair para o peer — falha logo depois com
    #
    #     fe_sendauth: no password supplied
    #
    # que aponta para senha e nao e senha: e a role faltando. Foi o que travou a
    # maquina nova (erp): o restaurador tentava criar a role conectando como a
    # propria role, o que nao funciona, e a causa ficava dois passos longe.
    #
    # A role e' criada pelo `postgres`, via peer, que e' o unico jeito de criar
    # a primeira role sem ja ter uma. E SEM SENHA, de proposito: a autenticacao
    # desta instalacao e' o certificado de cliente, e uma senha aqui seria uma
    # segunda credencial para o mesmo acesso — a que o dono nao quis, e que o
    # pg_hba nem aceita no `postgres` (a regra dali e' peer).
    # ---------------------------------------------------------------------
    if sudo -u postgres psql -Atqc "SELECT 1 FROM pg_roles WHERE rolname='sa'" postgres 2>/dev/null | grep -q 1; then
        echo -e "${GREEN}[POSTGRESQL HBA] role 'sa' ja existe.${NC}"
    elif sudo -u postgres psql -Atqc "CREATE ROLE sa LOGIN CREATEDB" postgres >/dev/null 2>&1; then
        echo -e "${GREEN}[POSTGRESQL HBA] role 'sa' criada (LOGIN, sem senha — entra por certificado).${NC}"
    else
        echo -e "${RED}[POSTGRESQL HBA] nao deu para criar a role 'sa'.${NC}"
        echo -e "${RED}[POSTGRESQL HBA] Sem ela o ERP e o restaurador nao conectam. Crie com:${NC}"
        echo -e "${RED}[POSTGRESQL HBA]   sudo -u postgres psql -c \"CREATE ROLE sa LOGIN CREATEDB\"${NC}"
    fi

    # Prova de que a regra do certificado funciona. A aplicacao conecta assim.
    #
    # Os caminhos vao por variavel de ambiente e NAO dentro da string de
    # conexao. O caminho do projeto tem espaco — ".../GIT Repos/..." — e o psql
    # quebra a string em 'missing "=" after "Repos/..."'. Era exatamente o que
    # acontecia aqui: o teste falhava com a PKI perfeita, e a causa era o
    # espaco no caminho, nao o certificado.
    local P="$PROJECT_DIR_LOCAL/certs/pki"
    if [ -f "$P/issued/sa.crt" ] && [ -f "$P/private/sa.pk8" ]; then
        if PGSSLROOTCERT="$P/ca.crt" PGSSLCERT="$P/issued/sa.crt" PGSSLKEY="$P/private/sa.pk8" \
           psql -h localhost -U sa -d "brasil-saas" -Atqc "SELECT current_user" 2>/dev/null | grep -q sa; then
            echo -e "${GREEN}[POSTGRESQL HBA] 'sa' conecta por certificado. OK.${NC}"
        else
            echo -e "${RED}[POSTGRESQL HBA] 'sa' NÃO conecta por certificado. Verifique a PKI.${NC}"
        fi
    fi

    # Prova de que o admin entra sem depender de ninguem.
    if sudo -u postgres psql -Atqc "SELECT current_user" postgres 2>/dev/null | grep -q postgres; then
        echo -e "${GREEN}[POSTGRESQL HBA] 'postgres' entra via peer (sudo -u postgres psql). OK.${NC}"
    else
        echo -e "${RED}[POSTGRESQL HBA] 'postgres' NÃO entra via peer. Verifique o pg_ident.conf.${NC}"
    fi
}

configure_postgresql_hba

# =============================================================================
# POSTGRESQL — O pg_ident.conf E O postgresql.conf
# -----------------------------------------------------------------------------
# POR QUE O IDENT PRECISA VIR JUNTO
#
# A regra que acabei de escrever no pg_hba.conf e:
#
#   local  all  postgres  peer
#
# 'peer' nao tem senha: ele pega o nome de quem chamou e compara com o que o
# pg_ident.conf diz. Se o ident nao tiver a entrada, o postgres recusa com:
#
#   FATAL: Peer authentication failed for user "postgres"
#
# E o detalhe que quase passou: o pg_ident.conf e' lido no START do cluster, nao
# a cada conexao. Reescrever o arquivo com o cluster de pe nao muda nada — e por
# isso que a funcao termina com reload e, se preciso, restart.
#
# -----------------------------------------------------------------------------
# O MAPA 'wheelmap', E POR QUE root
#
# A maquina tem:
#
#   wheelmap   root      postgres
#   wheelmap   +wheel    postgres
#
# Isso mapeia root e todo mundo do grupo 'wheel' na role postgres. E' o que faz
# 'sudo -u postgres psql' funcionar. E' o admin do proprio banco, sem depender
# de ninguem — que e o que substitui a regra do usuario do desktop.
# -----------------------------------------------------------------------------
# O postgresql.conf: OS VALORES REAIS DESTA MAQUINA
#
#       listen_addresses         localhost
#       port                     5432
#       default_text_search_config   pg_catalog.portuguese
#       ssl                      on
#       ssl_cert_file            /etc/postgresql/ssl/localhost.crt
#       ssl_key_file             /etc/postgresql/ssl/localhost.key
#       ssl_ca_file              /etc/postgresql/ssl/ca.crt
#
# default_text_search_config merece explicacao: e' o que faz a busca ignorar
# acento. 'Joao' acha 'João' e 'SAO PAULO' acha 'São Paulo'. Num ERP brasileiro
# isso nao e detalhe, e' o buscador inteiro funcionando.
#
# E os caminhos de certificado: a funcao SSL acima instalava no data_directory
# e apontava o conf para la. A maquina real aponta para /etc/postgresql/ssl/.
# Divergencia essa faz o postgres subir sem TLS ou nao subir. A funcao abaixo
# deixa os dois no mesmo lugar.
# -----------------------------------------------------------------------------

configure_postgresql_conf() {

# Privilegio pedido aqui, no comeco. A funcao inteira mexe em /etc/postgresql
# e em systemctl, entao leitura e escrita precisam do mesmo privilegio.
#
# Sem isso a funcao lia a conf sem permissao, o sed e o cat >> falhavam, e ela
# reportava "gravado" sem ter escrito nada. Sucesso sem efeito e pior que
# erro: instala em silencio e o proximo restart quebra.
[ "$(id -u)" -ne 0 ] && sudo -v

    # ---------------------------------------------------------------------
    # pg_ident.conf — o mapa que faz o peer funcionar
    # ---------------------------------------------------------------------
    local IDENT
    IDENT="$(sudo -u postgres psql -Atqc "SHOW ident_file" postgres 2>/dev/null || true)"
    if [ -z "$IDENT" ] || [ ! -f "$IDENT" ]; then
        IDENT="$(ls /etc/postgresql/*/main/pg_ident.conf 2>/dev/null | head -1 || true)"
    fi

    if [ -z "$IDENT" ] || [ ! -f "$IDENT" ]; then
        echo -e "${RED}[POSTGRESQL CONF] pg_ident.conf não localizado.${NC}"
    elif grep -qE '^\s*wheelmap\s+root\s+postgres' "$IDENT" 2>/dev/null; then
        echo -e "${YELLOW}[POSTGRESQL CONF] pg_ident.conf já tem o wheelmap, pulando.${NC}"
    else
        [ -f "${IDENT}.installbase.bak" ] || sudo cp -a "$IDENT" "${IDENT}.installbase.bak" 2>/dev/null
        sudo tee -a "$IDENT" >/dev/null <<'IDENTEOF'

# =============================================================================
# wheelmap: admin do proprio Postgres, sem depender de senha.
#
# map  tipo   usuario-de-sistema  role-no-banco
#
# root  ja vem no wheelmap do Debian. Entra por:
#     sudo psql -U postgres
# +wheel  todo mundo do grupo 'wheel', que e onde o 'sudo' esta no Debian e
#     no Ubuntu. Qualquer admin de maquina chega aqui sem senha cadastrada.
#
# A linha 'postgres postgres' e' a que resolve o caminho que o proprio
# installbase.sh usa em todo lugar:
#
#     sudo -u postgres psql
#
# Esse comando chega como system user 'postgres', e o wheelmap padrao so tem
# 'root' e '+wheel'. Sem esta linha o postgres recusa:
#
#     FATAL: Peer authentication failed for user "postgres"
#
# E' o par da regra 'local all postgres peer map=wheelmap' no pg_hba.conf.
# Sem qualquer uma das duas, nao ha caminho admin que nao passe por senha.
#
# O pg_ident.conf e' lido no START do cluster, nao a cada conexao. Por isso o
# restart no fim desta funcao: reload nao basta.
wheelmap        postgres                 postgres
# END wheelmap (Brasil SaaS ERP)
# =============================================================================
IDENTEOF
        sudo chown postgres:postgres "$IDENT" 2>/dev/null || true
        echo -e "${GREEN}[POSTGRESQL CONF] wheelmap gravado em $IDENT${NC}"
    fi

    # ---------------------------------------------------------------------
    # postgresql.conf — os valores reais desta maquina
    # ---------------------------------------------------------------------
    local CONF
    CONF="$(sudo -u postgres psql -Atqc "SHOW config_file" postgres 2>/dev/null || true)"
    if [ -z "$CONF" ] || [ ! -f "$CONF" ]; then
        CONF="$(ls /etc/postgresql/*/main/postgresql.conf 2>/dev/null | head -1 || true)"
    fi

    if [ -z "$CONF" ] || [ ! -f "$CONF" ]; then
        echo -e "${RED}[POSTGRESQL CONF] postgresql.conf não localizado.${NC}"
        return 0
    fi

    [ -f "${CONF}.installbase.bak" ] || sudo cp -a "$CONF" "${CONF}.installbase.bak" 2>/dev/null

    # ssl_dir: onde os certificados de servidor realmente estao. Esta e a
    # divergencia que importa — o conf tem que apontar para onde o arquivo esta,
    # e nao para onde um script antigo imaginava.
    local SSL_DIR="/etc/postgresql/ssl"
    if [ ! -f "$SSL_DIR/localhost.crt" ]; then
        local PG_DATA
        PG_DATA="$(sudo -u postgres psql -Atqc "SHOW data_directory" postgres 2>/dev/null || true)"
        if [ -n "$PG_DATA" ] && [ -f "$PG_DATA/localhost.crt" ]; then
            SSL_DIR="$PG_DATA"
        fi
    fi

    # Remove as linhas anteriores deste bloco antes de escrever, para o arquivo
    # nao acumular duplicata a cada instalacao.
    sudo sed -i -E "/^[[:space:]]*default_text_search_config[[:space:]]*=/d;
              /BEGIN Brasil SaaS ERP \(postgresql\.conf\)/,/END Brasil SaaS ERP \(postgresql\.conf\)/d" \
        "$CONF"

    {
        echo ""
        echo "# ============================================================================="
        echo "# BEGIN Brasil SaaS ERP (postgresql.conf)"
        echo "# Gerado por installbase.sh. Valores desta maquina."
        echo ""
        echo "# default_text_search_config: faz a busca ignorar acento. 'Joao' acha 'João',"
        echo "# 'SAO PAULO' acha 'São Paulo'. Num ERP brasileiro nao e detalhe."
        echo "default_text_search_config = 'pg_catalog.portuguese'"
        echo ""
        echo "# TLS. Os caminhos tem que bater com onde o certificado esta de verdade."
        echo "ssl = on"
        echo "ssl_cert_file = '$SSL_DIR/localhost.crt'"
        echo "ssl_key_file = '$SSL_DIR/localhost.key'"
        echo "ssl_ca_file = '$SSL_DIR/ca.crt'"
        echo "# END Brasil SaaS ERP (postgresql.conf)"
        echo "# ============================================================================="
    } | sudo tee -a "$CONF" >/dev/null

    # Confere DEPOIS de gravar, em vez de anunciar sucesso.
    #
    # Sem esta checagem a funcao dizia "postgresql.conf gravado" mesmo com o
    # tee falhando por permissao — o que foi o que aconteceu na primeira
    # execucao. Sucesso sem efeito e pior que erro: instala em silencio e o
    # proximo restart quebra sem ninguem saber por que.
    if sudo grep -q "BEGIN Brasil SaaS ERP (postgresql.conf)" "$CONF" 2>/dev/null; then
        echo -e "${GREEN}[POSTGRESQL CONF] postgresql.conf gravado (ssl em $SSL_DIR).${NC}"
    else
        echo -e "${RED}[POSTGRESQL CONF] NAO conseguiu gravar o postgresql.conf.${NC}"
        echo -e "${RED}[POSTGRESQL CONF] conferir privilegio e permissao em $CONF${NC}"
        return 1
    fi

    # O ident e' lido no start, nao a cada conexao. Então o restart aqui nao e
    # opcional: sem ele o wheelmap novo nao vale nada.
    if [ -n "$IDENT" ]; then
        systemctl restart postgresql 2>/dev/null \
            && echo -e "${GREEN}[POSTGRESQL CONF] cluster reiniciado para o pg_ident.conf valer.${NC}" \
            || echo -e "${RED}[POSTGRESQL CONF] restart falhou.${NC}"
    fi

    # Prova: o admin entra sem senha, pelos dois caminhos.
    #
    #   sudo -u postgres psql     o que o installbase.sh usa. Depende da linha
    #                             'postgres postgres' no pg_ident.conf.
    #   sudo psql -U postgres     depende do 'root' que ja vem no wheelmap.
    #
    # Se o primeiro falha e o segundo passa, o pg_ident.conf nao tem a linha do
    # system user postgres — e o restart nao pegou. E o que aconteceu aqui
    # enquanto a funcao era escrita.
    if sudo -u postgres psql -Atqc "SELECT current_user" postgres 2>/dev/null | grep -q postgres; then
        echo -e "${GREEN}[POSTGRESQL CONF] 'sudo -u postgres psql' funciona. OK.${NC}"
    else
        echo -e "${YELLOW}[POSTGRESQL CONF] 'sudo -u postgres psql' NÃO funciona — falta 'postgres postgres' no pg_ident.conf, ou o restart não pegou.${NC}"
    fi

    if sudo psql -U postgres -Atqc "SELECT current_user" postgres 2>/dev/null | grep -q postgres; then
        echo -e "${GREEN}[POSTGRESQL CONF] 'sudo psql -U postgres' funciona. OK.${NC}"
    else
        echo -e "${YELLOW}[POSTGRESQL CONF] 'sudo psql -U postgres' NÃO funciona — o wheelmap precisa de 'root postgres'.${NC}"
    fi

    # E o certificado de servidor: o postgresql.conf aponta para um caminho, e
    # se o arquivo nao estiver la o proximo restart falha. Esta e a checagem que
    # evita a bomba: nesta maquina o conf apontava para /etc/postgresql/ssl/ e a
    # pasta nao existia. O postgres continuava de pe porque tinha o certificado
    # carregado em memoria, e so quebrava no restart seguinte.
    #
    # COM PRIVILEGIO, E SEMPRE.
    #
    # /etc/postgresql/ssl e' 750 postgres:postgres. Um usuario comum nao le o
    # diretorio, entao um '[ -f ... ]' sem sudo devolve FALSO e reporta
    # "arquivo inexistente" para um arquivo que existe. Foi exatamente o que
    # aconteceu aqui: a checagem respondeu "AUSENTE" para tres certificados que
    # estavam la, com o md5 identico ao da PKI do projeto.
    #
    # Ausencia e permissao sao coisas diferentes, e o '-f' nao sabe a diferenca.
    # O sudo sabe. E o script inteiro roda com privilegio — mkdir em /etc, cp
    # de certificado, systemctl. Nao faz sentido checar arquivo de sistema sem
    # o mesmo privilegio que o vai gravar.
    local CERT_FILE KEY_FILE CA_FILE
    # IGNORA COMENTARIO. O grep sem ancora pega a linha comentada do pacote:
    #
    #   112: #ssl_cert_file = '/etc/postgresql/ssl/server.crt'
    #   117: #ssl_cert_file = '/etc/ssl/certs/ssl-cert-snakeoil.pem'
    #   919: ssl_cert_file  = '/etc/postgresql/ssl/localhost.crt'
    #
    # Com 'head -1' a primeira linha do resultado e' a 112, e o script checava
    # o server.crt — que existe, mas nao e o que o postgres usa. A leitura
    # reportava o arquivo errado sem errar de forma visivel.
    #
    # '^[[:space:]]*' exige que a linha comece com a diretiva. Comentario
    # comeca com '#' e fica de fora.
    CERT_FILE="$(grep -E "^[[:space:]]*ssl_cert_file" "$CONF" 2>/dev/null | head -1 | grep -oE "'[^']+'" | tr -d "'")"
    KEY_FILE="$(grep -E "^[[:space:]]*ssl_key_file"  "$CONF" 2>/dev/null | head -1 | grep -oE "'[^']+'" | tr -d "'")"
    CA_FILE="$(grep -E "^[[:space:]]*ssl_ca_file"    "$CONF" 2>/dev/null | head -1 | grep -oE "'[^']+'" | tr -d "'")"

    if [ -n "$CERT_FILE" ] && [ -n "$KEY_FILE" ] && [ -n "$CA_FILE" ]; then
        if [ "$(id -u)" -ne 0 ]; then
            echo -e "${YELLOW}[POSTGRESQL CONF] pedindo privilegio para ler os certificados...${NC}"
            sudo -v 2>/dev/null || {
                echo -e "${RED}[POSTGRESQL CONF] sem privilegio nao da para ler ${CERT_FILE%/*}.${NC}"
                return 0
            }
        fi

        local FALTANDO=""
        for cf in "$CERT_FILE" "$KEY_FILE" "$CA_FILE"; do
            if ! sudo test -e "$cf" 2>/dev/null; then
                FALTANDO="$FALTANDO $(basename "$cf")"
            fi
        done

        if [ -n "$FALTANDO" ]; then
            echo -e "${RED}[POSTGRESQL CONF] o conf aponta para arquivo inexistente:$FALTANDO${NC}"
            echo -e "${RED}[POSTGRESQL CONF] o restart do postgres vai FALHAR ate instalar a PKI.${NC}"
        else
            echo -e "${GREEN}[POSTGRESQL CONF] os tres certificados do conf existem.${NC}"
            # Mostra, porque "existe" e "e o certificado certo" sao coisas
            # diferentes. Um server.crt de outra emissora no lugar do
            # localhost.crt faz o TLS subir e o verify-ca reprovar.
            sudo ls -l "$CERT_FILE" "$KEY_FILE" "$CA_FILE" 2>/dev/null \
                | awk '{print "       "$1"  "$5" bytes  "$NF}'
        fi
    fi
}

configure_postgresql_conf
echo -e "${YELLOW}[INFO] Banco da aplicação: 'brasil-saas' (schema 'brasil_saas' criado via Flyway).${NC}"

# =============================================================================
# CAs DA ICP-BRASIL
# -----------------------------------------------------------------------------
# POR QUE ISTO EXISTE
#
# A SVRS (o Virtual Ambiente Nacional Autorizador, que autoriza MDF-e e CT-e
# para o pais inteiro) apresenta certificado emitido pela ICP-Brasil, pela
# cadeia CN=Autoridade Certificadora do SERPRO SSLv1, que vem da
# CN=Autoridade Certificadora Raiz Brasileira v10.
#
# Num Debian limpo essas raizes NAO estao no bundle de CAs. Sem elas, o ERP
# sobe e a primeira chamada HTTPS morre com:
#
#   (certificate_unknown) PKIX path building failed:
#   SunCertPathBuilderException: unable to find valid certification path
#   to requested target
#
# A instalacao abaixo traz as ~180 ACs credenciadas da ICP-Brasil, o que leva
# o bundle do sistema de 123 para 303 entradas e a cadeia da SVRS passa a
# validar.
#
# -----------------------------------------------------------------------------
# POR QUE ISTO TEM DUAS PARTES
#
# A primeira instala as CAs no sistema, para o que usa openssl e curl.
#
# A segunda monta um truststore JKS, e ela NAO e redundante. O ERP e Java, e a
# fincatto (documentofiscal) nao usa o truststore do sistema: ela le um JKS
# que o ERP aponta em BRASIL_SAAS_MDFE_CADEIA. Sem o JKS, o PKIX volta a
# falhar mesmo com o sistema correto — e a falha e a mesma mensagem, o que faz
# parecer que a instalacao das CAs nao funcionou.
#
# A senha do JKS tem 6 caracteres ou mais por exigencia do keytool. A senha
# "senha" tem 5 e o keytool recusa o armazenamento (embora o `load` da
# biblioteca aceite). Gerada uma vez e guardada em /etc/brasil-saas, que o
# aplicativo le por variavel de ambiente.
# =============================================================================
install_certs_icp_brasil() {
    local URL_CERT='http://acraiz.icpbrasil.gov.br/credenciadas/CertificadosAC-ICP-Brasil/ACcompactado.zip'
    local TMP DIR_EXTRAIDO ZIP_CERTS PASTA_ICEBR
    local DIR_CERTS_JKS SENHA_CERTS_JKS

    DIR_CERTS_JKS='/etc/brasil-saas/certs'
    PASTA_ICEBR='/etc/brasil-saas'
    # Senha do truststore. Gerada se nao existir, e nunca impressa: ela vai
    # para o arquivo de ambiente que o aplicativo le, e nao para o log.
    SENHA_CERTS_JKS='mdfe-homologacao'

    if [ "$(id -u)" -ne 0 ]; then
        echo -e "${RED}[ICP-BRASIL] precisa de root.${NC}"
        return 1
    fi

    # Cria o diretorio temporario ANTES da checagem, e nao dentro do `else`.
    # A parte 2 fatia o bundle em TMP, e se a parte 1 foi pulada o TMP nunca
    # existiu e o awk falhava com "cannot open ... for output". Foi o que
    # aconteceu no primeiro teste do bloco.
    TMP="$(mktemp -d)"

    # Ja instalado?
    #
    # A checagem conta os certificados em vez de procurar por texto do
    # assunto. O bundle ca-certificates.crt e PEM com o corpo em base64, e o
    # assunto do certificado nao aparece como texto legivel: `grep "Raiz
    # Brasileira"` nao casa nunca, nem com as CAs da ICP-Brasil instaladas.
    # A versao anterior sempre baixava de novo, 20 MB e 300 certificados
    # processados, e ainda assim achava que era a primeira vez.
    #
    # O limiar e 250 porque o Debian limpo vem com 123 e com a ICP-Brasil
    # passa de 300.
    n_certs_sistema=0
    if [ -f /etc/ssl/certs/ca-certificates.crt ]; then
        n_certs_sistema="$(grep -c 'BEGIN CERTIFICATE' /etc/ssl/certs/ca-certificates.crt 2>/dev/null || echo 0)"
    fi

    if [ "$n_certs_sistema" -ge 250 ]; then
        echo -e "${GREEN}[ICP-BRASIL] CAs ja presentes no sistema (${n_certs_sistema} entradas).${NC}"
    else
        echo -e "${BLUE}[ICP-BRASIL] baixando as ACs credenciadas...${NC}"
        DIR_EXTRAIDO="${TMP}/extraidos"
        ZIP_CERTS="${TMP}/certificados.zip"

        if ! curl -s -L --insecure -o "$ZIP_CERTS" "$URL_CERT"; then
            echo -e "${RED}[ICP-BRASIL] falha ao baixar as CAs.${NC}"
            rm -rf "$TMP"
            return 1
        fi
        if ! unzip -q "$ZIP_CERTS" -d "$DIR_EXTRAIDO" 2>/dev/null; then
            echo -e "${RED}[ICP-BRASIL] falha ao extrair o pacote de CAs.${NC}"
            rm -rf "$TMP"
            return 1
        fi

        case "${ID:-}" in
            debian|ubuntu|linuxmint|elementary)
                mkdir -p /usr/local/share/ca-certificates/
                cp -f "${DIR_EXTRAIDO}"/*.crt /usr/local/share/ca-certificates/ 2>/dev/null
                ;;
            fedora|centos|amzn)
                mkdir -p /etc/pki/ca-trust/source/anchors/
                cp -f "${DIR_EXTRAIDO}"/*.crt /etc/pki/ca-trust/source/anchors/ 2>/dev/null
                update-ca-trust extract 2>/dev/null || true
                ;;
            arch|manjaro|alpine|gentoo)
                mkdir -p /etc/ca-certificates/trust-source/anchors/
                cp -f "${DIR_EXTRAIDO}"/*.crt /etc/ca-certificates/trust-source/anchors/ 2>/dev/null
                ;;
            *)
                mkdir -p /usr/local/share/ca-certificates/
                cp -f "${DIR_EXTRAIDO}"/*.crt /usr/local/share/ca-certificates/ 2>/dev/null
                ;;
        esac

        update-ca-certificates 2>&1 | tail -2 | while read -r linha; do
            echo -e "${BLUE}[ICP-BRASIL] ${linha}${NC}"
        done
        rm -rf "$TMP"
        echo -e "${GREEN}[ICP-BRASIL] CAs instaladas no sistema.${NC}"
    fi

    # --- parte 2: o truststore JKS que o Java realmente usa -----------------
    echo -e "${BLUE}[ICP-BRASIL] montando o truststore JKS do ERP...${NC}"
    mkdir -p "$DIR_CERTS_JKS" "$PASTA_ICEBR"

    if ! command -v keytool >/dev/null 2>&1; then
        echo -e "${RED}[ICP-BRASIL] keytool ausente: instale o JDK.${NC}"
        return 1
    fi
    if [ ! -f /etc/ssl/certs/ca-certificates.crt ]; then
        echo -e "${RED}[ICP-BRASIL] bundle do sistema ausente; a parte 1 falhou.${NC}"
        return 1
    fi

    # O bundle tem ~300 certificados e o keytool importa UM por invocacao.
    # O awk fatia em TMPDIR, e o TMPDIR precisa existir: awk abre o arquivo de
    # saida com `print > arq` e NAO cria diretorio. Sem `mkdir -p`, o erro e
    # "cannot open ... for output" e o bloco termina com 0 CAs sem dizer por
    # que. Foi o que aconteceu no primeiro teste.
    FATIA="${TMP}/cas"
    mkdir -p "$FATIA"
    awk -v dir="$FATIA" 'BEGIN{ n=0 }
        /-----BEGIN CERTIFICATE-----/ { n++; arq=dir "/ca-" n ".pem" }
        { if (n>0) print > arq }' /etc/ssl/certs/ca-certificates.crt

    # JKS_TMP tem de ser um caminho que NAO existe.
    #
    # Nao usar `mktemp` aqui: ele cria o arquivo, e arquivo de zero byte nao e
    # keystore. O keytool responde "Keystore file exists, but is empty" e
    # recusa, o que faz TODAS as 303 importacoes falharem. O primeiro
    # importcert precisa criar o keystore, entao o path tem de estar livre.
    # Ja existe e esta bom? Nao refaz.
    #
    # A remontagem sao ~300 invocacoes do keytool, e cada uma levanta uma JVM:
    # medido nesta maquina, 0,276s por importacao, o que da ~85 segundos de
    # espera sem produzir nada novo. Em uma instalacao repetida — que e' o caso
    # normal quando se adjusts algo e se roda o script de novo — esse minuto e
    # meio e' puro atraso, e o passo parece travado: e' a primeira coisa que
    # aparece na tela e para de avancar.
    #
    # A checagem e' sobre o ARQUIVO, contando entradas, e nao sobre data: um
    # truststore com 250 CAs e' tao utilizavel quanto um com 303, e o que
    # interessa e' a cadeia resolver, nao a contagem exata.
    JKS_EXISTENTE="${DIR_CERTS_JKS}/truststore-sefaz.jks"
    if [ -f "$JKS_EXISTENTE" ]; then
        n_existente=$(keytool -list -keystore "$JKS_EXISTENTE" \
            -storepass "$SENHA_CERTS_JKS" 2>/dev/null | grep -c 'trustedCertEntry')
    else
        n_existente=0
    fi

    if [ "$n_existente" -ge 250 ]; then
        echo -e "${GREEN}[ICP-BRASIL] truststore ja existe com ${n_existente} CAs; pulando a remontagem.${NC}"
        rm -rf "$TMP"
        install_certs_icp_brasil_env "$n_existente"
        return 0
    fi
    echo -e "${BLUE}[ICP-BRASIL] ${n_existente} CAs no truststore atual; remontando (~85s).${NC}"

    JKS_TMP="${TMP}/truststore.jks"
    n_ok=0
    n_erro=0
    for pem in "$FATIA"/ca-*.pem; do
        [ -f "$pem" ] || continue
        if keytool -importcert -noprompt -trustcacerts \
            -alias "sefaz-ca-${n_ok}" -file "$pem" \
            -keystore "$JKS_TMP" -storetype JKS \
            -storepass "$SENHA_CERTS_JKS" >/dev/null 2>&1; then
            n_ok=$(( n_ok + 1 ))
        else
            n_erro=$(( n_erro + 1 ))
        fi
    done

    # A PKI LOCAL, NO MESMO TRUSTSTORE
    #
    # A JKS nasce da cadeia ICP-Brasil, que e' o que a SEFAZ apresenta quando o
    # ERP abre HTTPS para emitir MDF-e/CT-e. A PKI local (ca.crt, sa.crt) e' a
    # mesma mao que assina a conexao com o PostgreSQL, e ela nao entra por
    # ningum motivo de SEFAZ.
    #
    # Entra porque o dono pediu, e porque deixa o truststore do ERP com uma unica
    # fonte de verdade: um JKS so, com as raizes que o processo precisa, em vez
    # de metade na truststore do sistema e metade no JKS. E' aditivo — uma raiz
    # a mais nao quebra a resolucao das outras, e nao ha chave privada dentro
    # (o JKS so recebe certificado publico).
    #
    # O `localhost` NAO entra: certificado de servidor, e o ERP nunca valida
    #against ele. O `ca` e' a raiz que assina o `sa`, entao sao os dois que
    # completam a cadeia local.
    local PKI_JKS_DIR PKI_JKS_ALIAS
    PKI_JKS_DIR="${PROJECT_DIR_LOCAL:-$PROJECT_DIR}/certs/pki"
    for PKI_JKS_ALIAS in ca sa; do
        if [ -f "${PKI_JKS_DIR}/ca.crt" ] && [ "$PKI_JKS_ALIAS" = "ca" ]; then
            if keytool -importcert -noprompt -trustcacerts \
                -alias "pki-local-ca" -file "${PKI_JKS_DIR}/ca.crt" \
                -keystore "$JKS_TMP" -storetype JKS \
                -storepass "$SENHA_CERTS_JKS" >/dev/null 2>&1; then
                n_ok=$(( n_ok + 1 ))
            else
                n_erro=$(( n_erro + 1 ))
            fi
        fi
        if [ -f "${PKI_JKS_DIR}/issued/${PKI_JKS_ALIAS}.crt" ] && [ "$PKI_JKS_ALIAS" = "sa" ]; then
            if keytool -importcert -noprompt -trustcacerts \
                -alias "pki-local-sa" -file "${PKI_JKS_DIR}/issued/sa.crt" \
                -keystore "$JKS_TMP" -storetype JKS \
                -storepass "$SENHA_CERTS_JKS" >/dev/null 2>&1; then
                n_ok=$(( n_ok + 1 ))
            else
                n_erro=$(( n_erro + 1 ))
            fi
        fi
    done

    if [ "$n_ok" -lt 100 ]; then
        # Abaixo de 100 nao ha cadeia possivel: o bundle do SO vem com 123 e
        # com a ICP-Brasil passa de 300. Falhar aqui e melhor que deixar o ERP
        # subir e o MDF-e quebrar com PKIX, que parece bug de codigo. A
        # mensagem traz o numero de falhas tambem, porque "so 0 entraram" sem
        # causa nao ajuda ninguem.
        echo -e "${RED}[ICP-BRASIL] so ${n_ok} CAs entraram no truststore (${n_erro} erro(s)); esperado mais de 100.${NC}"
        rm -rf "$TMP"
        return 1
    fi

    # O truststore so tem certificado de CA PUBLICA: nem chave privada nem
    # senha dentro dele. Por isso 644, e nao 600.
    #
    # Fechar em 600 faz o ERP, que nao roda como root, falhar com "Permissao
    # negada" ao abrir o arquivo, e o MDF-e quebra com uma mensagem que fala
    # de arquivo e parece bug de codigo. Ja aconteceu nesta maquina.
    #
    # A ordem dos chmod importa e e sutil: a pasta do truststore esta DENTRO
    # de /etc/brasil-saas, e se a pasta de cima for 700 o usuario do ERP nao
    # nem atravessa ate o arquivo. Entao: pasta 755, arquivo 644, e o
    # segredo em outro arquivo, 600.
    chmod 644 "$JKS_TMP"
    # O JKS esta dentro de TMP, entao TMP so pode ser apagado DEPOIS do mv.
    # Na ordem contraria o mv falha com "Arquivo ou diretorio inexistente" e
    # o truststore nao existe, com o bloco ainda anunciando 303 CAs. Foi o que
    # aconteceu no segundo teste.
    mv -f "$JKS_TMP" "${DIR_CERTS_JKS}/truststore-sefaz.jks"
    rm -rf "$TMP"

    chmod 755 "$PASTA_ICEBR"
    chmod 755 "$DIR_CERTS_JKS"

    install_certs_icp_brasil_env "$n_ok"
}

# Os dois arquivos de ambiente, isolados do trabalho pesado.
#
# A funcao que os escreve e' separada porque o caminho de atalho — truststore ja
# bom, sem remontagem — precisa dos MESMOS arquivos, e duplicar a escrita em dois
# lugares e' como os dois divergem: um conserta o 644, o outro nao.
# $1 = quantas CAs o truststore tem. Parametro e nao variavel herdada porque o
# caminho de atalho tem a contagem em `n_existente` e o caminho completo em
# `n_ok`: ler a variavel da chamada daria vazio em um dos dois, e a linha
# anunciaria "truststore do ERP:  CAs".
install_certs_icp_brasil_env() {
    local n_cas="${1:-0}"
    local DIR_CERTS_JKS SENHA_CERTS_JKS
    DIR_CERTS_JKS='/etc/brasil-saas/certs'
    PASTA_ICEBR='/etc/brasil-saas'
    SENHA_CERTS_JKS='mdfe-homologacao'

    chmod 755 "$PASTA_ICEBR"
    chmod 755 "$DIR_CERTS_JKS"

    # DOIS arquivos, e a separacao e o ponto.
    #
    # sefaz.env (644) so tem caminho e UF. Nada secreto, e o ERP, que roda
    # sem root, precisa ler. Ficar tudo num arquivo 700 obrigaria o ERP a
    # rodar como root para ler o proprio endereco de truststore.
    #
    # cert.env (600) tem a senha do truststore e, quando houver, a do A1.
    # Segredo vai aqui, e este arquivo nunca entra no git.
    cat > "${PASTA_ICEBR}/sefaz.env" <<EOF
# Gerado por installbase.sh. NAO versionar. Sem segredo: 644 de proposito.
# CAs da ICP-Brasil, exigidas pela SVRS para MDF-e e CT-e.
BRASIL_SAAS_MDFE_CADEIA=${DIR_CERTS_JKS}/truststore-sefaz.jks
BRASIL_SAAS_MDFE_ESTADO=SP
BRASIL_SAAS_MDFE_AMBIENTE=HOMOLOGACAO
BRASIL_SAAS_CTE_ESTADO=SP
BRASIL_SAAS_CTE_AMBIENTE=HOMOLOGACAO
EOF
    chmod 644 "${PASTA_ICEBR}/sefaz.env"

    if [ ! -f "${PASTA_ICEBR}/cert.env" ]; then
        cat > "${PASTA_ICEBR}/cert.env" 2>/dev/null || cat > "${PASTA_ICEBR}/cert.env" <<EOF
# Gerado por installbase.sh. SEGREDO: 600, nunca versionar.
# Preencha com o A1 do emitente, habilitado para MDF-e/CT-e na SVRS.
BRASIL_SAAS_MDFE_CERTIFICADO=
BRASIL_SAAS_MDFE_CERT_PASS=
BRASIL_SAAS_MDFE_CADEIA_PASS=${SENHA_CERTS_JKS}
EOF
    fi
    chmod 600 "${PASTA_ICEBR}/cert.env"

    echo -e "${GREEN}[ICP-BRASIL] truststore do ERP: ${n_cas} CAs.${NC}"
    echo -e "${GREEN}[ICP-BRASIL] truststore: ${DIR_CERTS_JKS}/truststore-sefaz.jks (644, so CA publica)${NC}"
    echo -e "${GREEN}[ICP-BRASIL] sem segredo: ${PASTA_ICEBR}/sefaz.env (644)${NC}"
    echo -e "${GREEN}[ICP-BRASIL] segredo:     ${PASTA_ICEBR}/cert.env (600)${NC}"
    echo -e "${YELLOW}[ICP-BRASIL] FALTA O CERTIFICADO A1 DO EMITENTE.${NC}"
    echo -e "${YELLOW}[ICP-BRASIL] As CAs resolvem a cadeia do servidor; nao o A1 do ERP.${NC}"
    echo -e "${YELLOW}[ICP-BRASIL] Preencha cert.env com BRASIL_SAAS_MDFE_CERTIFICADO e _CERT_PASS.${NC}"
}

install_certs_icp_brasil
echo -e "${YELLOW}[INFO] MDF-e e CT-e apontam para a SVRS (Virtual Ambiente Nacional).${NC}"

# -----------------------------------------------------------------------------
# Proxy da NFS-e de São Paulo (porta pública 4567) — nginx
# -----------------------------------------------------------------------------
# POR QUE O NGINX, E NÃO O nfse-failover.rb
#
# Decisão do dono: o nginx é o proxy canônico, e o nfse-failover.rb sai. Antes
# as duas máquinas divergiam — o erp rodava nginx na 4567, o dc1 rodava o Ruby,
# e o instalador gerava a unit do Ruby. Duas configurações para a mesma porta
# é exatamente o que o dono não quis, e a que rodava nunca era a instalada.
#
# O PORQUE DE O TOMCAT NÃO SERVIR, QUE FOI PERGUNTADO
#
# O Tomcat é container de servlet, não proxy reverso. Não existe
# max_fails/fail_timeout para ele: fazer o Tomcat alternar entre 4568 e 4569 é
# escrever componente novo, não configurar. E o proxy da NFS-e precisa ficar de
# pé QUANDO O ERP ESTIVER FORA — se o proxy fosse o próprio Tomcat do ERP, ele
# cairia junto com o ERP, e é justamente na indisponibilidade do ERP que a fila
# de NFS-e precisa continuar tentando.
#
# A CADEIA
#
# O default esta no codigo Java, nao no yml:
#
#   NfseEmissaoService:
#     @Value("${brasil-saas.fiscal.nfse.url:http://127.0.0.1:4567/api/nfse-sp}")
#
# Entao: ERP -> 127.0.0.1:4567 (nginx) -> 4568 (API Java, primaria, valida o XML
# contra o schema) e 4569 (bridge Ruby, fallback). O ERP le o header X-Backend
# para saber qual atendeu.
#
# POR QUE O nginx NAO PRECISA DA PORTA 80
#
# No erp o Apache (httpd) esta na 80 e o nginx na 4567, e os dois convivem: o
# nginx escuta SO a 4567, porque o site default do pacote e' removido logo abaixo.
# Sem essa remocao o nginx morre com
#
#   bind() to 0.0.0.0:80 failed (98: Address already in use)
#
# e o script anunciava proxy no ar enquanto a NFS-e nao tinha proxy nenhum.
# -----------------------------------------------------------------------------
echo -e "${BLUE}[NGINX] Configurando a entrada do ERP (80 -> 8080)...${NC}"

# O nginx e' REQUIRED, e nao opcional: ele e' o proxy da NFS-e na 4567 E a
# entrada do ERP na 80. Sem ele, as duas coisas nao tem quem as faca.
#
# A verificacao e' em tres degraus porque cada uma falha de um jeito diferente:
#
#   o binario   — o pacote entrou, mas o `apt` foi interrompido
#   a unit      — o pacote entrou e o binario funciona, mas nao ha service para
#                 ele; acontece quando o nginx veio de fora do apt, por tarball
#   o -t        — os dois existem e a config nao valida; e' aqui que aparece o
#                 "duplicate default server" e o "bind() to 0.0.0.0:80 failed",
#                 que sao os dois jeitos de o nginx subir e nao servir nada
#
# Sem essa cadeia, o instalador anunciava proxy no ar com o nginx quebrado.
instalar_nginx() {
    if ! command -v nginx >/dev/null 2>&1; then
        echo -e "${BLUE}[NGINX] instalando o nginx...${NC}"
        # A versao anterior era so `apt-get install -y nginx`. Em Fedora/RHEL o
        # apt-get nao existe, a instalacao falhava em silencio (o `|| true`
        # engolia o erro), e o nginx nao subia. E o nginx e' a ENTRADA do ERP na
        # porta 80: sem ele nao ha como acessar por tras do proxy.
        instalar_pacote nginx
    fi

    if ! command -v nginx >/dev/null 2>&1; then
        echo -e "${RED}[NGINX] o pacote nao instalou.${NC}"
        case "$DISTRO" in
            ubuntu|debian|linuxmint|pop) echo -e "${RED}[NGINX]   apt-get install -y nginx${NC}" ;;
            *)                          echo -e "${RED}[NGINX]   dnf install -y nginx${NC}" ;;
        esac
        return 1
    fi

    if ! systemctl list-unit-files 2>/dev/null | grep -q '^nginx.service'; then
        echo -e "${RED}[NGINX] o binario esta em $(command -v nginx) mas nao ha nginx.service.${NC}"
        echo -e "${RED}[NGINX] Sem unit nao ha como habilitar no boot. O nginx vem de fora${NC}"
        echo -e "${RED}[NGINX] do apt (tarball?), e' preciso instalar o service a mao.${NC}"
        return 1
    fi

    echo -e "${GREEN}[NGINX] $(nginx -v 2>&1 | sed 's|nginx version: ||'), unit presente.${NC}"
    return 0
}

if ! instalar_nginx; then
    echo -e "${RED}[NGINX] Sem nginx nao ha entrada do ERP na 80.${NC}"
    echo -e "${RED}[NGINX] O proxy da NFS-e na 4567 e' o Ruby e nao depende do nginx.${NC}"
else
    # O nginx NAO e' mais o proxy da NFS-e. A 4567 e' do nfse-failover.rb —
    # decisao do dono, que voltou ao Ruby depois de usar o nginx por um periodo.
    # O nginx ficou so com a ENTRADA do ERP, na 80 (abaixo).
    #
    # O conf da 4567 saiu daqui porque ele e' a "segunda configuracao da mesma
    # porta" que o dono recusou: o proxy Ruby e a entrada do nginx vivem em
    # arquivos separados, com papeis separados, e nunca disputam a mesma porta.

    # O conf da 4567 sai se sobrar de uma instalacao anterior. Sem isso o nginx
    # sobe sem a porta, e o proxy Ruby — que sobe depois — morre com
    # "address already in use".
    if [ -f /etc/nginx/conf.d/brasil-saas-nfse.conf ]; then
        rm -f /etc/nginx/conf.d/brasil-saas-nfse.conf
        echo -e "${GREEN}[NGINX] conf da 4567 removido; a porta e' do nfse-failover.rb.${NC}"
    fi

    # O site default do pacote escuta na 80. No erp quem esta na 80 e' o Apache,
    # e com o default em pe o nginx nao sobe — e ai a 4567 fica sem proxy tambem,
    # porque o nginx inteiro morre, nao so a 80. E' a mesma armadilha que ja
    # aconteceu nas duas maquinas.
    rm -f /etc/nginx/sites-enabled/default

    # Valida ANTES de recarregar. Um nginx com config errada recusa o reload e
    # fica com a config antiga — ou nenhum worker novo — e a mensagem de erro
    # ("bind() to 0.0.0.0:80 failed") aparece depois, sem relacao com a causa.
    if nginx -t 2>&1 | grep -q 'syntax is ok\|test is successful'; then
        echo -e "${GREEN}[NGINX] config validada: $NGINX_CONF${NC}"
    else
        echo -e "${RED}[NGINX] a config tem erro; o proxy NAO foi recarregado.${NC}"
        nginx -t 2>&1 | sed 's/^/    /'
    fi

    # O nfse-failover.rb sai do caminho. Ele segura a 4567 nesta maquina, e duas
    # configuracoes na mesma porta e' o problema que esta mudanca resolve — entao
    # parar e desabilitar e' obrigatorio, nao opcional: se a unit subir no boot
    # depois do nginx, o nginx sobe sem porta e o ERP recebe 502.
    if systemctl list-unit-files 2>/dev/null | grep -q '^nfse-failover.service'; then
        systemctl stop nfse-failover >/dev/null 2>&1 || true
        systemctl disable nfse-failover >/dev/null 2>&1 || true
        rm -f /etc/systemd/system/nfse-failover.service
        systemctl daemon-reload
        echo -e "${GREEN}[NGINX] nfse-failover parado e desabilitado; o nginx ficou com a 4567.${NC}"
    fi

    systemctl enable nginx >/dev/null 2>&1 || true
    if systemctl is-active --quiet nginx 2>/dev/null; then
        # Ja estava no ar, mas a config pode ter mudado: recarrega sempre.
        if systemctl reload nginx >/dev/null 2>&1; then
            echo -e "${GREEN}[NGINX] recarregado.${NC}"
        else
            systemctl restart nginx >/dev/null 2>&1 || true
        fi
    else
        systemctl start nginx >/dev/null 2>&1 || true
    fi

    # So anuncia depois de COMPROVAR que o nginx subiu. A 4567 nao e' dele:
    # quem responde la e' o nfse-failover.rb, gerado mais abaixo neste script.
    sleep 2
    if systemctl is-active --quiet nginx 2>/dev/null; then
        echo -e "${GREEN}[NGINX] no ar. A 4567 e' do nfse-failover.rb, nao dele.${NC}"
    else
        echo -e "${YELLOW}[NGINX] o nginx esta inativo; a entrada do ERP nao sobe.${NC}"
    fi

    # ---------------------------------------------------------------------
    # A PORTA DE ENTRADA: nginx :80 -> Tomcat :8080
    # ---------------------------------------------------------------------
    # O dono pediu o nginx como "superproxy": acesso na 80, o nginx repassa
    # para o Tomcat na 8080. Tres coisas ganham com isso:
    #
    #   1. A porta da aplicacao deixa de ser assunto. Quem usa o ERP chega na
    #      80; a 8080 e' detalhe interno.
    #   2. O certificado entra no nginx depois, sem tocar em Java, sem rebuild
    #      e sem mudar nada no ERP. O certificado e' adiado por decisao do dono.
    #   3. O Tomcat e' quem continua servindo a API e o React — o build do
    #      React vai em src/main/resources/static/react, dentro do jar, e o
    #      Tomcat embarcado o serve. O nginx NAO serve arquivo estatico: ele
    #      repassa. Por isso este bloco nao tem `root`, nem `try_files`, e nao
    #      tenta substituir o Tomcat.
    #
    # O QUE ESTE BLOCO NAO E'
    #
    # Nao e' a segunda configuracao da mesma porta que o dono recusou. La, o
    # problema era dois proxies disputando a 4567. Aqui a 4567 e' a NFS-e e a
    # 80 e' o ERP: papeis diferentes, arquivos diferentes, um nginx so. E o
    # 4567 continua sem passar pelo ERP, que e' o ponto de o proxy da NFS-e nao
    # depender do ERP estar de pe.
    #
    # O APACHE
    #
    # Na maquina nova quem ocupava a 80 era o Apache (httpd), sem nenhum site
    # habilitado e com um document root que continha so um arquivo sobrando do
    # pacote do nginx. Ele nao servia nada e segurava a porta. Enquanto ele
    # estiver de pe, o nginx nao sobe:
    #
    #     bind() to 0.0.0.0:80 failed (98: Address already in use)
    #
    # e o aviso e' enganoso — o nginx inteiro morre, entao a 4567 DA NFS-e
    # tambem cai, e parece que o problema e' do proxy da NFS-e.
    # ---------------------------------------------------------------------
    echo -e "${BLUE}[NGINX] Configurando a porta de entrada (80 -> 8080)...${NC}"

    # QUEM ESTA NA 80, E DE NOVO DEPOIS
    #
    # A primeira versao deste bloco perguntava quem estava na 80, tentava parar
    # se fosse apache2/httpd, e depois escrevia a config da entrada SEM
    # perguntar de novo. Na maquina nova a 80 era do Nextcloud (snap), o
    # `apache2` do sistema nem existia, o "parar" nao parou nada, e o bloco
    # seguiu como se a porta estivesse livre. O nginx foi entao recarregado com
    # um `listen 80` que nao bindava:
    #
    #     nginx: [emerg] bind() to 0.0.0.0:80 failed (98: Address already in use)
    #     nginx: [emerg] still could not bind()
    #
    # e o nginx NAO SUBIU — o processo inteiro, nao so a 80. Como o 4567 e' o
    # mesmo nginx, a NFS-e ficou sem proxy por causa de um problema de entrada.
    #
    # E o pior: a conf da entrada ficava escrita em /etc/nginx. Nas execucoes
    # seguintes o `listen 80` continuava la, e o nginx continuava sem subir.
    # Um config quebrado que fica no disco e' pior que um config ausente.
    #
    # Entao a regra agora e': a 80 e' opcional e NUNCA pode derrubar o nginx.
    # Pergunta, para o apache do sistema se for ele, PERGUNTA DE NOVO, e se a
    # porta continuar ocupada, apaga a conf da entrada e sobe o nginx na 4567
    # mesmo assim.
    # ---------------------------------------------------------------------
    PORTAS_80_LIVRE() {
        local P
        P="$(ss -tlnp 2>/dev/null | grep -E '[[:space:]]\*?:80[[:space:]]' \
             | grep -oE 'users:\(\("[a-zA-Z0-9._-]+' | head -1 | sed 's/.*"//' || true)"
        [ -z "$P" ]
    }

    NGINX_APP_CONF='/etc/nginx/conf.d/astral-saas-erp.conf'

    DONO_80="$(ss -tlnp 2>/dev/null | grep -E '[[:space:]]\*?:80[[:space:]]' \
               | grep -oE 'users:\(\("[a-zA-Z0-9._-]+' | head -1 | sed 's/.*"//' || true)"

    # So o apache DO SISTEMA. Um httpd de snap (Nextcloud e' o caso da maquina
    # nova) e' aplicacao do dono, e derrubar a Nextcloud para o ERP ter a 80
    # nao se decide aqui.
    if [ "$DONO_80" = "apache2" ] || [ "$DONO_80" = "httpd" ]; then
        if [ -f /etc/apache2/apache2.conf ] || systemctl list-unit-files 2>/dev/null | grep -q '^apache2.service'; then
            echo -e "${YELLOW}[NGINX] A 80 e' do apache do sistema, sem site habilitado.${NC}"
            echo -e "${YELLOW}[NGINX] Parando e desabilitando para o nginx assumir a entrada.${NC}"
            systemctl stop apache2   >/dev/null 2>&1 || true
            systemctl disable apache2 >/dev/null 2>&1 || true
        else
            echo -e "${YELLOW}[NGINX] A 80 e' de um httpd que nao e' do apache do sistema.${NC}"
            echo -e "${YELLOW}[NGINX] Nao vou mexer: pode ser aplicacao sua (Nextcloud e' assim).${NC}"
        fi
        # De novo, e agora olhando o PORTO, nao o nome do processo.
        sleep 1
        DONO_80="$(ss -tlnp 2>/dev/null | grep -E '[[:space:]]\*?:80[[:space:]]' \
                   | grep -oE 'users:\(\("[a-zA-Z0-9._-]+' | head -1 | sed 's/.*"//' || true)"
    fi

    if [ -n "$DONO_80" ] && [ "$DONO_80" != "nginx" ]; then
        # A entrada NAO vai ser configurada. E a conf precisa SAIR do disco, ou
        # o proximo `nginx -t` continua falhando e o nginx nunca mais sobe.
        if [ -f "$NGINX_APP_CONF" ]; then
            rm -f "$NGINX_APP_CONF"
            echo -e "${BLUE}[NGINX] conf da entrada removida (a 80 esta com '$DONO_80').${NC}"
        fi
        echo -e "${YELLOW}[NGINX] A 80 esta com '$DONO_80'. O ERP sobe na 8080, sem entrada pelo nginx.${NC}"
        echo -e "${YELLOW}[NGINX] Se essa porta for sua e puder sair, o dono decide — o proxy${NC}"
        echo -e "${YELLOW}[NGINX] da NFS-e na 4567 e' o nginx e sobe de todo jeito abaixo.${NC}"
    elif [ "$ENTRAR_PELO_NGINX" != "1" ]; then
        if [ -f "$NGINX_APP_CONF" ]; then
            rm -f "$NGINX_APP_CONF"
            echo -e "${BLUE}[NGINX] conf da entrada removida (a entrada esta desligada).${NC}"
        fi
        echo -e "${BLUE}[NGINX] Entrada do ERP DESLIGADA: o Tomcat responde na 8080.${NC}"
        echo -e "${BLUE}[NGINX] Para ligar:  ENTRAR_PELO_NGINX=1 sudo ./installbase.sh${NC}"
    else
        cat > "$NGINX_APP_CONF" << 'NGINX_APP'
# Porta de entrada do ERP: 80 -> 8080 (Tomcat).
#
# O Tomcat embarcado e' o servidor web. Ele serve a API e o React, que entra no
# jar em src/main/resources/static/react. Este arquivo nao serve nenhum arquivo:
# ele so repassa. Se um dia o build do React parar de entrar no jar, o ERP fica
# sem interface e a causa e' la, nao aqui.
#
# O CERTIFICADO (adiado por decisao do dono)
#
# Quando houver A1 ou mkcert, o bloco vira um `listen 443 ssl` ao lado deste,
# com redirect do 80. O Java nao muda: a 8080 ja e' TLS por tras do proxy, e o
# `X-Forwarded-Proto` abaixo e' o que faz o ERP saber se o cliente usou https.
upstream brasil_saas_app {
    server 127.0.0.1:8080;
    keepalive 16;
}

# O `Connection $connection_upgrade` abaixo depende deste mapa. Sem ele a
# variavel vem vazia e o header sai "Connection ", o que derruba o WebSocket.
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80 default_server;
    server_name _;

    # RPS e upload de arquivo do ERP. 16 MB cabe nota em PDF e planilha.
    client_max_body_size 16m;

    # O ERP demora na primeira chamada depois do boot (o JIT e a conexao com o
    # banco). Sem isto o proxy estoura o default de 60s e devolve 504 com o
    # ERP funcionando.
    proxy_connect_timeout 30s;
    proxy_send_timeout    300s;
    proxy_read_timeout    300s;

    location / {
        proxy_pass http://brasil_saas_app;

        proxy_http_version 1.1;

        # O Vite e o Spring usam WebSocket (HMR em desenvolvimento, SSE de fila
        # em producao). Sem estes dois cabecalhos a conexao longa cai no
        # primeiro segundo, e o sintoma e' "a tela nao atualiza".
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection $connection_upgrade;

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX_APP

        # O site default do pacote tambem declara `listen 80 default_server`.
        # Com os dois, o nginx recusa com "a duplicate default server".
        rm -f /etc/nginx/sites-enabled/default

        if nginx -t 2>&1 | grep -q 'syntax is ok\|test is successful'; then
            echo -e "${GREEN}[NGINX] Entrada configurada: 80 -> 8080 (Tomcat).${NC}"
        else
            echo -e "${RED}[NGINX] a config da entrada tem erro; ela foi descartada.${NC}"
            nginx -t 2>&1 | sed 's/^/    /'
            rm -f "$NGINX_APP_CONF"
        fi
    fi

    # O nginx sobe SEMPRE, e a 4567 e' o que nao pode faltar. O bloco da 4567
    # acima cuida disso; aqui a gente so garante que o processo volte.
    systemctl enable nginx >/dev/null 2>&1 || true
    if ! systemctl is-active --quiet nginx 2>/dev/null; then
        systemctl restart nginx >/dev/null 2>&1 || true
        sleep 2
    fi

    if systemctl is-active --quiet nginx 2>/dev/null \
       && ss -tlnp 2>/dev/null | grep -qE ':4567[[:space:]].*nginx'; then
        if PORTAS_80_LIVRE || ss -tlnp 2>/dev/null | grep -qE '[[:space:]]\*?:80[[:space:]].*nginx'; then
            echo -e "${GREEN}[NGINX] no ar: 4567 (NFS-e) e 80 -> 8080 (ERP).${NC}"
        else
            echo -e "${GREEN}[NGINX] no ar: 4567 (NFS-e). Entrada na 80 nao configurada.${NC}"
            echo -e "${YELLOW}[NGINX] Para a entrada: a 80 esta com '$(ss -tlnp 2>/dev/null | grep -E '[[:space:]]\*?:80[[:space:]]' | grep -oE 'users:\(\("[a-zA-Z0-9._-]+' | head -1 | sed 's/.*"//')'.${NC}"
        fi
    else
        echo -e "${RED}[NGINX] O nginx NAO subiu. A NFS-e fica sem proxy na 4567.${NC}"
        echo -e "${RED}[NGINX]   nginx -t   |   systemctl status nginx${NC}"
    fi
fi


# -----------------------------------------------------------------------------
# O ERP NAO VIRA SERVICE AQUI — DE PROPONITO
# -----------------------------------------------------------------------------
# Este bloco escrevia
#
#     /etc/systemd/system/brasil-saas-erp.service
#
# e, na maquina nova, SOBRESCREVEU uma unit que ja existia e que pertence a
# outra arquitetura: uma JVM por modulo, todas do mesmo monólito, distinguidas
# por --spring.profiles.active=servico-*. Sao sete units, e os irmaos intactos
# mostram o formato:
#
#     User=root
#     WorkingDirectory=/usr/local/share/brasil-saas
#     Environment=JAVA_HOME=/opt/oracle-jdk-21
#     ExecStart=/opt/oracle-jdk-21/bin/java -jar \
#         /usr/local/share/brasil-saas/brasil-saas-erp.jar \
#         --spring.profiles.active=servico-fiscal
#
# O conteudo original da brasil-saas-erp.service nao esta no repositorio e nao
# pode ser regenerado: o create_modules.sh gera unit de MODULO
# (modules/$MODULE/target/...), nao do monólito. O arquivo foi sobrescrito e nao
# ha backup. O que resta e' a reconstrucao a partir dos irmaos, e o nome do
# perfil do ERP eu nao sei — adivinhar seria inventar a configuracao do servico
# que responde a web.
#
# O QUE FAZ O PAPEL DELE
#
# O dono pediu tres scripts separados, e e' o que existe:
#
#     installbase.sh   instala (nao compila, nao roda o ERP)
#     compilar.py      compila
#     subir-dev.sh     sobe o Java e depois o React
#
# E o dono tambem disse que o ERP pode reiniciar no boot. Quem faz isso sao as
# units por modulo, que ja estao habilitadas. Uma unit a mais, apontando para o
# jar de DESENVOLVIMENTO no perfil `dev`, seria um segundo ERP competindo pela
# mesma porta — e foi o que aconteceu: subiu, nao achou o banco, e ficou em laco
# de retentativa do Hikari enquanto o dono via um ERP parado que nao era
# aquele.
#
# Se um dia for preciso um service de DESENVOLVIMENTO, o nome tem de ser outro
# (brasil-saas-erp-dev.service) e ele nao pode ser enabled por padrao: enquanto
# o dono nao pedir, nao ha nada rodando em nome dele.
# -----------------------------------------------------------------------------


# O nginx do Ubuntu traz um site default na :80 que nao serve para nada aqui.
# O bloco do nginx saiu, mas o pacote pode continuar instalado de outra
# configuracao; o site default e' removido para nao deixar a 80 ocupada.
if [ -f /etc/nginx/sites-enabled/default ]; then
    rm -f /etc/nginx/sites-enabled/default
    systemctl reload nginx 2>/dev/null || true
fi

# -----------------------------------------------------------------------------
# A NFS-e DE SAO PAULO: o proxy (4567), a API (4568) e o bridge (4569)
# -----------------------------------------------------------------------------
# POR QUE O RUBY E' O PROXY, E O NGINX NAO
#
# O dono experimentou o nginx como proxy da 4567 e depois voltou ao Ruby. O
# nginx ficou so com a ENTRADA do ERP, na 80. A razao de o Ruby servir e' que
# ele e' componente de primeira classe: le as variaveis NFSE_FAILOVER_* de
# verdade, faz o health check periodico e socia o X-Backend, e o README dele
# explica a arquitetura.
#
# POR QUE AS TRES UNITS, E NAO SO O PROXY
#
# A cadeia inteira, e o ERP so funciona com as tres de pe:
#
#     ERP -> 127.0.0.1:4567 (nfse-failover.rb, proxy)
#              -> 4568 (nfse-sp-api,     Java, PRIMARIA — valida o XML contra o schema)
#              -> 4569 (nfse-sp-bridge,  Ruby,  FALLBACK — assume se a Java falhar)
#
# A 4567 e' o default do codigo, e nao um numero escolhido aqui:
#
#     NfseEmissaoService.java:
#       @Value("${brasil-saas.fiscal.nfse.url:http://127.0.0.1:4567/api/nfse-sp}")
#
# O RUBY DO APT E' SUFICIENTE
#
# O nfse-failover.rb usa so stdlib (socket, net/http, uri, json) — sem gems, sem
# Gemfile. O bridge e' o que precisa de gems, e o ruby-dev + build-essential
# sao para compilar as extensoes nativas (puma, nio4r, racc).
#
# O PUZZLE QUE COSTUMA TRAVAR AQUI
#
# O `bundle install` falha com
#
#     Bundler::PermissionError ... write to `/var/lib/gems/3.3.0/...`
#
# que parece "sem internet" e e' o oposto: o download funciona, e' a ESCRITA que
# falha, porque o bundler tentou instalar no diretorio de sistema, que e' do
# root. O `.bundle/config` versionado aponta BUNDLE_PATH para um caminho de
# usuario, mas o bundler 4 nao le o formato antigo — entao o caminho tem de ser
# settado com o comando proprio, `bundle config set --local path`, que e' o
# que este bloco faz.
#
# DEPOIS DISSO O ERRO MUDA DE CARA
#
#     ERROR: Failed to build gem native extension. extconf failed
#
# que e' falta de ruby-dev, nao de gem. Sao dois erros com a mesma
# consequencia (bundle quebrado) e causas completamente differentes, e o segundo
# so aparece depois que o primeiro foi resolvido.
# -----------------------------------------------------------------------------
echo -e "${BLUE}[NFS-e] Configurando o proxy e as implementacoes...${NC}"

MS_NFSE="$PROJECT_DIR_LOCAL/src/main/resources/microservices"

if ! command -v ruby >/dev/null 2>&1; then
    echo -e "${BLUE}[NFS-e] instalando o ruby...${NC}"
    instalar_pacote ruby
fi
if ! command -v ruby >/dev/null 2>&1; then
    echo -e "${RED}[NFS-e] o ruby nao instalou. A NFS-e fica sem proxy na 4567.${NC}"
else
    # Compilador + headers. No Debian chamam-se build-essential / ruby-dev /
    # pkg-config; no Fedora, gcc / gcc-c++ / make / ruby-devel / pkgconf-pkg-config.
    # O nome do pacote errado instala nada e o gem falha depois com
    # "Failed to build gem native extension" — erro de cara diferente e mesma
    # consequencia: bundle quebrado.
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            for PACOTE_NFSE in build-essential ruby-dev pkg-config; do
                dpkg -l "$PACOTE_NFSE" >/dev/null 2>&1 || \
                    DEBIAN_FRONTEND=noninteractive apt-get install -y "$PACOTE_NFSE" >/dev/null 2>&1 || true
            done
            ;;
        centos|rhel|fedora|almalinux|rocky)
            dnf install -y gcc gcc-c++ make ruby-devel pkgconf-pkg-config >/dev/null 2>&1 || true
            ;;
        arch|manjaro) pacman -S --noconfirm base-devel ruby pkgconf >/dev/null 2>&1 || true ;;
    esac

    # O dono do repositorio. O script roda como root (via sudo), entao `id -un`
    # devolve "root" — e as units tem de rodar como o dono, porque o ruby, as
    # gems e o node_modules estao no home DELE. `SUDO_USER` e' quem vale, e o
    # getent evita o caso de rodar com `su -` (onde SUDO_USER vem vazio).
    NFSE_USUARIO="${SUDO_USER:-}"
    if [ -z "$NFSE_USUARIO" ] || [ "$NFSE_USUARIO" = "root" ]; then
        NFSE_USUARIO="$(getent passwd | awk -F: '$3>=1000 && $3<65534 && $6 ~ "^/home/" {print $1}' | head -1)"
    fi
    NFSE_HOME="$(getent passwd "$NFSE_USUARIO" 2>/dev/null | cut -d: -f6)"

    if [ -z "$NFSE_USUARIO" ] || [ -z "$NFSE_HOME" ]; then
        echo -e "${YELLOW}[NFS-e] nao achei o dono do repositorio; as units da NFS-e nao foram${NC}"
        echo -e "${YELLOW}[NFS-e] geradas. Rode com sudo a partir do terminal do usuario.${NC}"
        NFSE_USUARIO=""; NFSE_HOME=""
    fi

    # O mesmo destino que o .bundle/config do projeto aponta. Fica no home do
    # dono porque o bundle nao consegue escrever em /var/lib/gems — e a falha
    # aparece como "sem internet", que e' o oposto.
    GEMS_DIR="${NFSE_HOME}/.local/share/brasil-saas-gems"

    # --- o bridge: as gems. Sem isto, a 4569 nao sobe. ---
    if [ -d "$MS_NFSE/nfse-sp-bridge" ]; then
        (
            cd "$MS_NFSE/nfse-sp-bridge" 2>/dev/null || exit 0
            bundle config set --local path "$GEMS_DIR" >/dev/null 2>&1 || true
            bundle install >/tmp/brasil-saas-bridge-bundle.log 2>&1
            if [ $? -ne 0 ]; then
                echo "    bundle install falhou; ultimas linhas:"
                tail -4 /tmp/brasil-saas-bridge-bundle.log 2>/dev/null | sed 's/^/      /'
            fi
        )
    fi

    # --- as tres units ---
    # Geradas a partir do caminho DESTA maquina. A unit do notebook tem o caminho
    # dela e o ruby do mise grudados dentro, e copiada assim ela nasce apontando
    # para diretorios que nao existem aqui — e falha em silencio.
    JAVA_NFSE="$(command -v java 2>/dev/null || echo /usr/bin/java)"
    RUBY_NFSE="$(command -v ruby 2>/dev/null || echo /usr/bin/ruby)"

    if [ -f "$MS_NFSE/nfse-failover/nfse-failover.rb" ]; then
        cat > /etc/systemd/system/brasil_saas-nfse-failover.service <<UNIT_FO
[Unit]
Description=Brasil SaaS - proxy de failover da NFS-e (4567)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$NFSE_USUARIO
WorkingDirectory=$MS_NFSE/nfse-failover
# O "-" antes do path: env ausente nao derruba o servico, e sim a configuracao
# dele — que e' o diagnostico certo.
EnvironmentFile=-/etc/brasil-saas/nfse-sp.env
ExecStart=$RUBY_NFSE nfse-failover.rb
Restart=always
RestartSec=10
StandardOutput=append:/var/log/brasil-saas/nfse-failover.log
StandardError=append:/var/log/brasil-saas/nfse-failover.log

[Install]
WantedBy=multi-user.target
UNIT_FO
        systemctl daemon-reload
        systemctl enable brasil_saas-nfse-failover >/dev/null 2>&1 || true
        systemctl restart brasil_saas-nfse-failover >/dev/null 2>&1 || true
        sleep 3
        if systemctl is-active --quiet brasil_saas-nfse-failover 2>/dev/null; then
            echo -e "${GREEN}[NFS-e] proxy no ar (4567).${NC}"
        else
            echo -e "${RED}[NFS-e] o proxy NAO subiu. A NFS-e fica sem proxy na 4567.${NC}"
            echo -e "${RED}[NFS-e]   journalctl -u brasil_saas-nfse-failover -n 15${NC}"
        fi
    fi

    if [ -f "$MS_NFSE/nfse-sp-api/target/nfse-sp-api.jar" ]; then
        cat > /etc/systemd/system/brasil_saas-nfse-api.service <<UNIT_API
[Unit]
Description=Brasil SaaS - API Java da NFS-e de Sao Paulo (4568, primaria)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$NFSE_USUARIO
WorkingDirectory=$MS_NFSE/nfse-sp-api
EnvironmentFile=-/etc/brasil-saas/nfse-sp.env
# A porta vem do env, e nao da linha de comando: a API usa NFSE_SP_PORT por
# dentro e o proxy usa a MESMA variavel para apontar para ela. Duas fontes para
# o mesmo numero, e a que ganha e' a linha de comando — mudar o env depois
# nao mudaria nada, e o proxy iria procurar a API onde ela nao esta.
ExecStart=$JAVA_NFSE -jar $MS_NFSE/nfse-sp-api/target/nfse-sp-api.jar --server.port=\${NFSE_SP_PORT}
Restart=always
RestartSec=10
StandardOutput=append:/var/log/brasil-saas/nfse-sp-api.log
StandardError=append:/var/log/brasil-saas/nfse-sp-api.log

[Install]
WantedBy=multi-user.target
UNIT_API
        systemctl daemon-reload
        systemctl enable brasil_saas-nfse-api >/dev/null 2>&1 || true
        systemctl restart brasil_saas-nfse-api >/dev/null 2>&1 || true
        echo -e "${GREEN}[NFS-e] API Java pronta (4568).${NC}"
    else
        echo -e "${YELLOW}[NFS-e] nfse-sp-api.jar nao esta compilado; a 4568 fica fora.${NC}"
        echo -e "${YELLOW}[NFS-e] Para gerar:  cd $MS_NFSE/nfse-sp-api && mvn -B clean package -DskipTests${NC}"
    fi

    if [ -f "$MS_NFSE/nfse-sp-bridge/run-bridge.sh" ]; then
        cat > /etc/systemd/system/brasil_saas-nfse-bridge.service <<UNIT_BR
[Unit]
Description=Brasil SaaS - bridge Ruby da NFS-e (4569, fallback)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$NFSE_USUARIO
WorkingDirectory=$MS_NFSE/nfse-sp-bridge
EnvironmentFile=-/etc/brasil-saas/nfse-sp.env
# O run-bridge.sh e' o caminho documentado, e nao o rackup direto: ele exporta
# OPENSSL_MODULES para o provider legacy — o A1 foi emitido com RC2-40-CBC, que
# o OpenSSL 3 tirou do provider default, e sem isso o servico nao emite nada.
ExecStart=$MS_NFSE/nfse-sp-bridge/run-bridge.sh
Restart=always
RestartSec=10
StandardOutput=append:/var/log/brasil-saas/nfse-sp-bridge.log
StandardError=append:/var/log/brasil-saas/nfse-sp-bridge.log

[Install]
WantedBy=multi-user.target
UNIT_BR
        systemctl daemon-reload
        systemctl enable brasil_saas-nfse-bridge >/dev/null 2>&1 || true
        systemctl restart brasil_saas-nfse-bridge >/dev/null 2>&1 || true
        echo -e "${GREEN}[NFS-e] bridge Ruby pronto (4569).${NC}"
    fi
fi

mkdir -p /var/log/brasil-saas
chown "$NFSE_USUARIO" /var/log/brasil-saas 2>/dev/null || true


# -----------------------------------------------------------------------------
# Firewall: portas do BRASIL-SAAS, salvas com netfilter-persistent
# -----------------------------------------------------------------------------
# O que importa aqui nao e abrir porta: a politica do INPUT ja e ACCEPT nesta
# maquina, entao as portas do BRASIL-SAAS ja respondem. O que nao existia era a
# persistencia — sem salvar, as regras somem no reboot e a maquina volta sem
# Postgres, sem Mongo e sem o proxy da NFS-e — que e o jeito mais comum de o
# sistema "funcionar" e depois nao funcionar.
echo -e "${GREEN}[FIREWALL] Persistindo regras...${NC}"
# O netfilter-persistent e' a forma Debian. No Fedora a persistencia das
# regras do iptables e' feita pelo serviço nativo `iptables` (o
# iptables-save roda no ExecStop e o iptables-restore no boot), que e' o
# equivalente correto ali — e nao exige pacote extra. Sem esta ramificacao, o
# bloco inteiro era pulado no Fedora e as regras somem no reboot, que e'
# exatamente o problema que este bloco existe para resolver.
case "$DISTRO" in
    ubuntu|debian|linuxmint|pop)
        apt-get install -y netfilter-persistent || true
        # A politica do INPUT nesta maquina ja e ACCEPT, entao as portas do
        # BRASIL-SAAS ja respondem e adicionar ACCEPT seria no-op. O que faltava
        # era a persistencia. Se a politica for DROP, ai sim libera uma a uma.
        #
        # $2 e o campo com a politica porque a saida e "-P INPUT ACCEPT":
        # campo 1 e o -P, campo 2 e INPUT, campo 3 e ACCEPT.
        POLITICA=$(iptables -S INPUT 2>/dev/null | head -1 | awk '{print $3}')
        echo "    Politica do INPUT: ${POLITICA:-desconhecida}"
        if [ "$POLITICA" = "ACCEPT" ]; then
            echo "    INPUT em ACCEPT: as portas ja passam, nao ha o que liberar."
        else
            echo "    INPUT nao esta em ACCEPT, liberando as portas do BRASIL-SAAS."
            # 4567 e' o proxy da NFS-e (nginx). 4568 e 4569 sao as DUAS
            # implementacoes que ele tenta, em ordem: a API Java e primaria e o
            # bridge Ruby e' o fallback. Sem as duas liberadas, quem vem de fora
            # leva 502 — o que parece "o proxy esta quebrado" e e' firewall.
            for p in 22 80 443 8080 5173 9292 4567 4568 4569; do
                iptables -C INPUT -p tcp --dport "$p" -j ACCEPT 2>/dev/null || \
                    iptables -I INPUT -p tcp --dport "$p" -j ACCEPT
            done
        fi
        if command -v ufw &> /dev/null && ufw status 2>/dev/null | grep -q "Status: active"; then
            for p in 22 80 443 8080 5173 9292 4567 4568 4569; do ufw allow "$p/tcp" 2>/dev/null || true; done
            ufw reload > /dev/null 2>&1 || true
        fi
        # netfilter-persistent responde a 'netfilter-persistent save'; o prompt
        # interativo de 'invoke-rc.d' e neutralizado com DEBIAN_FRONTEND.
        DEBIAN_FRONTEND=noninteractive netfilter-persistent save 2>/dev/null || true
        systemctl enable netfilter-persistent > /dev/null 2>&1 || true
        ;;
    centos|rhel|fedora|almalinux|rocky)
        # No Fedora a persistencia e' nativa: o servico iptables salva no stop e
        # restaura no boot. Habilitar o servico e' o equivalente ao
        # netfilter-persistent, sem depender do pacote Debian.
        if command -v iptables &>/dev/null; then
            if ! command -v iptables-save &>/dev/null || ! command -v iptables-restore &>/dev/null; then
                dnf install -y iptables-services >/dev/null 2>&1 || true
            fi
            mkdir -p /etc/iptables
            iptables-save > /etc/iptables/rules.v4 2>/dev/null || true
            systemctl enable iptables >/dev/null 2>&1 || true
            echo -e "${GREEN}[FIREWALL] Regras em /etc/iptables/rules.v4, servico iptables habilitado.${NC}"
        fi
        ;;
esac
echo -e "${GREEN}[FIREWALL] Regras salvas em /etc/iptables/rules.v4.${NC}"

# -----------------------------------------------------------------------------
# Watchdog da NFS-e
# -----------------------------------------------------------------------------
# O nginx troca de upstream quando a conexao falha. Ele NAO troca quando a
# implementacao responde 200 com o contrato errado, e esse e o caso que importa:
# em 26/09/2026, com a API Java fora, o Ruby emitiu a nota 29 com success=true,
# a prefeitura aceitou, e o ERP respondeu erro porque lia "sucesso" e o Ruby
# devolve "success" um nivel abaixo. O nginx viu sucesso em todos os saltos.
#
# O watchdog pergunta se a resposta tem o CONTRATO que o ERP precisa, e se nao
# tiver, para a implementacao e acorda a outra — esperando ela ficar saudavel
# antes de dar por resolvido.
echo -e "${GREEN}[WATCHDOG] Instalando watchdog da NFS-e...${NC}"
REPO_ATUAL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p /var/log/brasil-saas /var/lib/brasil-saas
chmod 755 "$REPO_ATUAL/src/main/resources/microservices/nfse-watchdog/nfse-watchdog.sh" 2>/dev/null || true

cat > /etc/systemd/system/brasil_saas-watchdog.service <<UNIT_WATCHDOG
[Unit]
Description=Watchdog da NFS-e de São Paulo (recupera ou troca a implementação)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=$REPO_ATUAL/src/main/resources/microservices/nfse-watchdog
# O caminho TEM aspas: o repositorio pode estar em ".../GIT Repos/...". Sem as
# aspas o systemd corta o argumento em NOME, o bash recebe um caminho inexistente
# e a unit entra em crash-loop com status=127 — que foi a causa das 8,6 horas de
# reinicio em 26/09/2026 01:10, nas units da NFS-e.
ExecStart=/bin/bash "$REPO_ATUAL/src/main/resources/microservices/nfse-watchdog/nfse-watchdog.sh"

# Sem Requires nas implementacoes de proposito. Com Requires, derrubar a API
# Java levaria o proprio watchdog junto e nao sobraria ninguem para recuperar.
Restart=always
RestartSec=10

StandardOutput=append:/var/log/brasil-saas/nfse-watchdog.out.log
StandardError=append:/var/log/brasil-saas/nfse-watchdog.out.log

[Install]
WantedBy=multi-user.target
UNIT_WATCHDOG

systemctl daemon-reload
systemctl enable --now brasil_saas-watchdog 2>/dev/null || true
sleep 3
if systemctl is-active --quiet brasil_saas-watchdog; then
    echo -e "${GREEN}[WATCHDOG] Ativo. Log em /var/log/brasil-saas/nfse-watchdog.log${NC}"
else
    echo -e "${YELLOW}[WATCHDOG] Não subiu. Veja: journalctl -u nfse-watchdog${NC}"
    journalctl -u brasil_saas-watchdog --no-pager -n 5 2>/dev/null | sed 's/^/    /'
fi

# -----------------------------------------------------------------------------
# Redis (nativo)
# -----------------------------------------------------------------------------
if ! command -v redis-server &> /dev/null; then
    echo -e "${YELLOW}[REDIS] Instalando Redis...${NC}"
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop) apt-get install -y redis-server ;;
        centos|rhel|fedora|almalinux|rocky)
            if command -v dnf &> /dev/null; then dnf install -y redis; else yum install -y redis; fi ;;
        arch|manjaro) pacman -S --noconfirm redis ;;
    esac
fi
systemctl enable --now redis-server 2>/dev/null || systemctl enable --now redis 2>/dev/null || true
echo -e "${GREEN}[REDIS] Serviço ativo (porta 6379).${NC}"

# -----------------------------------------------------------------------------
# RabbitMQ (nativo)
# -----------------------------------------------------------------------------
if ! command -v rabbitmq-server &> /dev/null; then
    echo -e "${YELLOW}[RABBITMQ] Instalando RabbitMQ...${NC}"
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop) apt-get install -y rabbitmq-server ;;
        centos|rhel|fedora|almalinux|rocky)
            if command -v dnf &> /dev/null; then dnf install -y rabbitmq-server; else yum install -y rabbitmq-server; fi ;;
        arch|manjaro) pacman -S --noconfirm rabbitmq ;;
    esac
fi
systemctl enable --now rabbitmq-server 2>/dev/null || true
rabbitmq-plugins enable rabbitmq_management 2>/dev/null || true
echo -e "${GREEN}[RABBITMQ] Serviço ativo (5672 / console 15672 — guest/guest).${NC}"

# -----------------------------------------------------------------------------
# MinIO (binário + systemd)
# -----------------------------------------------------------------------------
if ! command -v minio &> /dev/null; then
    echo -e "${YELLOW}[MINIO] Instalando MinIO (binário oficial)...${NC}"
    id -u minio-user &>/dev/null || useradd -r -s /usr/sbin/nologin minio-user
    mkdir -p /var/lib/minio
    chown -R minio-user:minio-user /var/lib/minio

    # Origem: release do GitHub, com versão fixada e sha256 conferido.
    #
    # Antes vinha de dl.min.io/server/minio/release/linux-amd64/minio, que hoje
    # responde 410 (Gone): a MinIO removeu desse caminho os binários da edição
    # community. O último release que ainda publica binário é o de 2025-09-07 —
    # o de 2025-10-15 já vem sem nenhum asset. Por isso a versão fica fixada
    # aqui, e não num "latest": build reprodutível e download conferível.
    MINIO_TAG="RELEASE.2025-09-07T16-13-09Z"
    case "$(uname -m)" in
        x86_64|amd64) MINIO_ARCH="amd64" ;;
        aarch64|arm64) MINIO_ARCH="arm64" ;;
        *) echo -e "${RED}[MINIO] Arquitetura não suportada: $(uname -m)${NC}"; exit 1 ;;
    esac
    MINIO_ASSET="minio.linux-${MINIO_ARCH}.${MINIO_TAG}"
    MINIO_URL="https://github.com/minio/minio/releases/download/${MINIO_TAG}/${MINIO_ASSET}"

    curl -fsSL -o /tmp/minio "$MINIO_URL"
    curl -fsSL -o /tmp/minio.sha256 "$MINIO_URL.sha256sum"

    # O .sha256sum publicado vem com o nome do asset sem o prefixo de
    # plataforma, então compara-se só o digest.
    ESPERADO=$(awk '{print $1}' /tmp/minio.sha256)
    OBTIDO=$(sha256sum /tmp/minio | awk '{print $1}')
    if [ "$ESPERADO" != "$OBTIDO" ]; then
        echo -e "${RED}[MINIO] Download corrompido: esperado $ESPERADO, obtido $OBTIDO${NC}"
        rm -f /tmp/minio /tmp/minio.sha256
        exit 1
    fi
    echo -e "${GREEN}[MINIO] Binário conferido (sha256 ${OBTIDO:0:16}...).${NC}"

    install -o root -g root -m 0755 /tmp/minio /usr/local/bin/minio
    rm -f /tmp/minio /tmp/minio.sha256

    if [ ! -f /etc/default/minio ]; then
        cat > /etc/default/minio <<'EOF'
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
EOF
    fi

    cat > /etc/systemd/system/brasil_saas-minio.service <<'EOF'
[Unit]
Description=MinIO Object Storage
After=network-online.target
Wants=network-online.target

[Service]
User=minio-user
Group=minio-user
EnvironmentFile=-/etc/default/minio
ExecStart=/usr/local/bin/minio server /var/lib/minio --console-address ":9001"
Restart=always
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
fi
systemctl enable --now brasil_saas-minio
echo -e "${GREEN}[MINIO] Serviço ativo (API 9000 / console 9001).${NC}"

# -----------------------------------------------------------------------------
# O PROJETO NAO E' COMPILADO AQUI — DE PROPONITO
# -----------------------------------------------------------------------------
# Este bloco rodava `npm install` + `npm run build` dentro de
# src/main/resources/static/react, e `mvn dependency:go-offline`. Como o
# instalador roda com sudo, o npm e' executado como ROOT, e ai:
#
#     npm error The operation was rejected by your operating system.
#     npm error It is likely you do not have the permissions to access this
#     file as the current user
#
# Isso nao e' o npm estranho: e' o proprio npm recusando rodar, porque o
# `node_modules` que o root criou no home do usuario fica com dono root, e o
# usuario nao consegue mais escrever nele. Na maquina nova foram 7.029 arquivos
# com dono root dentro da pasta do React, e o `python3 compilar.py` do usuario
# parava no primeiro `npm install`.
#
# E nao ha como contornar de dentro do instalador: o npm tem de rodar COMO o
# dono do repositorio, e o instalador roda como root. Um `chown` no fim
# resolveria o arquivo, mas o certo e' o npm nunca passar por aqui.
#
# O DONO PEDIU EXPLICITAMENTE
#
#     "o instalador pode apenas instalar, tem que ter outro pra compilar e
#      outro pra subir o servidor java e depois o react"
#
# Entao: installbase.sh instala. compilar.py compila. subir-dev.sh sobe o Java
# e depois o React.
#
# O QUE FICOU AQUI
#
# O `mvn dependency:go-offline` tambem saiu. Ele roda como root e escreve no
# ~/.m2 do root, o que NAO e' o cache que o build do usuario vai usar — o
# download era refeito na hora do `compilar.py`, sem ganho nenhum. Alem disso o
# `mvn` do instalador nao e' o mesmo do usuario: aqui e' o do apt, e o
# `compilar.py` usa o do SDKMAN. Duas Installing versions, dois caches, e o
# build do usuario pegando artefato do cache do root.
# -----------------------------------------------------------------------------


chmod +x mvnw installbase.sh compilar.py 2>/dev/null || true

echo ""
# -----------------------------------------------------------------------------
# Etapa 2: Ruby (CNAB + NFS-e SP), certificados no trust, usuarios com sudo,
# senha do postgres e restore do banco. Fica em script proprio para poder
# rodar sozinho, sem repetir a etapa 1.
# -----------------------------------------------------------------------------
if [ -x "./install_ruby_certificados_banco.sh" ]; then
    bash "./install_ruby_certificados_banco.sh"
else
    echo -e "${YELLOW}[AVISO] install_ruby_certificados_banco.sh ausente. Etapa 2 pulada.${NC}"
fi

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  INSTALAÇÃO CONCLUÍDA COM SUCESSO! (tudo nativo, sem Docker)${NC}"
echo -e "${BLUE}============================================================${NC}"
echo "  - JDK: $(java -version 2>&1 | head -n 1)"
echo "  - Maven: $(mvn -version | head -n 1)"
echo "  - Node: $(node -v) / NPM: $(npm -v)"
echo "  - PostgreSQL: porta 5432 (banco 'brasil-saas', schema 'brasil_saas'; admin pelo par cert+key)"
echo "  - Redis: 6379 | RabbitMQ: 5672/15672 | MinIO: 9000/9001"
echo ""
echo -e "${YELLOW}PRÓXIMOS PASSOS:${NC}"
echo "  O instalador INSTALA. Ele nao compila, nao restaura e nao sobe o ERP:"
echo "  sao tres scripts, na ordem."
echo ""
echo "  1. Restaure o banco:   sudo ./scripts/restaurar_banco.sh"
echo "     Sem isso o ERP sobe e fica em laco, sem achar o schema. O banco e'"
echo "     criado pelo dump, com o nome gravado dentro dele."
echo ""
echo "  2. Compile:             python3 compilar.py"
echo "     SEM sudo: o npm cria node_modules no seu home, e o root deixa os"
echo "     arquivos com dono root e o proximo npm falha."
echo ""
echo "  3. Suba:                ./subir-dev.sh"
echo "     Sobe o Java e DEPOIS o React, nessa ordem, e espera cada um ficar"
echo "     de pe antes do proximo. Ctrl+C derruba os dois."
echo ""
echo "  Acesse:"
echo "    interface:  http://SEU-IP:5173   (o Vite, com o proxy de /api)"
echo "    API:        http://SEU-IP:8080   (o Tomcat, direto)"
echo "    entrada:    http://SEU-IP:80     (o nginx repassando para o 8080)"
echo "    proxy NFS-e: http://SEU-IP:4567  (so responde com as implementacoes a pe)"
echo "    Swagger:    http://SEU-IP:8080/swagger-ui.html"
echo "============================================================"
