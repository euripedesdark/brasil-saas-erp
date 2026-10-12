#!/usr/bin/env bash
# =============================================================================
# Brasil SaaS ERP — etapa 2 do instalador: Ruby, certificados, banco e
# restore. Chamado pelo installbase.sh ao final da etapa 1.
#
# Pode rodar isolado:  ./install_ruby_certificados_banco.sh
#
# Propriedade: SrvCloud Soluções — Euripedes Batista de Paiva Junior
# =============================================================================
set -uo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Senhas do ambiente. CHANGE_ME_VIA_ENV e a senha de 'euripedes' (sudo), do usuario
# 'euripedes' no app e do superusuario postgres; ver README_INSTALACAO.md.
SENHA_PADRAO="${BRASIL_SAAS_SENHA:-}"  # obrigatorio via env em automacao; vazio = pedir interativo
SENHA_SUDO="${BRASIL_SAAS_SENHA_SUDO:-$SENHA_PADRAO}"

PKI_DESTINO="${BRASIL_SAAS_PKI:-$HOME/.local/share/brasil-saas-certs/pki}"

# Executa com privilegio. Nao usa `sudo -n` porque o certificado de tempo
# pode ter expirado; alimenta o prompt por stdin. A senha vem da variavel,
# nao fica na linha de comando (nao aparece no `ps` nem no historico).
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo -S -p"
    fi
fi

as_root() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif [ -n "$SUDO" ]; then
        printf '%s\n' "$SENHA_SUDO" | sudo -S -p '' "$@"
    else
        "$@"
    fi
}

echo -e "${BLUE}============================================================${NC}"
echo -e "${BLUE}  Etapa 2 — Ruby, certificados, banco e restore${NC}"
echo -e "${BLUE}============================================================${NC}"

detect_distro() {
    if [ -f /etc/os-release ]; then . /etc/os-release; echo "$ID"
    elif command -v lsb_release >/dev/null 2>&1; then lsb_release -si | tr '[:upper:]' '[:lower:]'
    else uname -s | tr '[:upper:]' '[:lower:]'; fi
}
DISTRO=$(detect_distro)

echo -e "Distribuição: ${YELLOW}$DISTRO${NC}"

# =============================================================================
# 1. RUBY
# =============================================================================
# Ruby é usado pelos microsserviços: boleto_cnab_api (financeiro) e
# nfse-sp-bridge (nota de serviço São Paulo). Nenhum dos dois é Java.
#
# Usamos mise porque compilar do fonte leva ~6 minutos e falha se a máquina
# tiver OpenSSL 4 (o ruby 3.3 não compila). mise baixa binário pronto.

instalar_ruby_mise() {
    echo -e "${GREEN}[RUBY] Instalando via mise...${NC}"
    export PATH="$HOME/.local/bin:$PATH"

    if ! command -v mise >/dev/null 2>&1; then
        curl -fsSL https://mise.run | sh
    fi
    [ -f "$HOME/.local/bin/mise" ] || { echo -e "${RED}[RUBY] mise não instalou.${NC}"; return 1; }
    chmod +x "$HOME/.local/bin/mise" 2>/dev/null || true

    # ruby@3 -> ultima 3.x
    mise use -g ruby@3 >/dev/null 2>&1

    # ativa no shell novo
    if ! grep -q 'mise activate' "$HOME/.bashrc" 2>/dev/null; then
        echo 'eval "$(~/.local/bin/mise activate bash)"' >> "$HOME/.bashrc"
    fi

    local rb
    rb="$(mise which ruby 2>/dev/null)"
    [ -n "$rb" ] && echo -e "${GREEN}[RUBY] $(ruby -v 2>/dev/null || "$rb" -v)${NC}"
}

instalar_ruby_fonte() {
    # alternativa quando mise nao esta disponivel (rede corporativa, arq sem binario)
    echo -e "${YELLOW}[RUBY] mise indisponível; compilando do fonte (lento)...${NC}"
    local ver="3.4.11"
    local tmp; tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/ruby.tar.gz" "https://cache.ruby-lang.org/pub/ruby/3.4/ruby-${ver}.tar.gz" || return 1
    tar xzf "$tmp/ruby.tar.gz" -C "$tmp"
    ( cd "$tmp/ruby-${ver}" && ./configure --prefix="$HOME/.local/ruby" --disable-install-doc --enable-shared >/dev/null 2>&1
      make -j"$(nproc)" >/dev/null 2>&1 && make install >/dev/null 2>&1 ) || return 1

    # psych precisa de libyaml; o mkmf nem procura /usr/include
    if [ ! -f "$HOME/.local/ruby/lib/ruby/3.4.0/x86_64-linux/psych.so" ]; then
        ( cd "$tmp/ruby-${ver}/ext/psych" \
          && "$HOME/.local/ruby/bin/ruby" extconf.rb --with-libyaml-dir=/usr >/dev/null 2>&1 \
          && make >/dev/null 2>&1 ) || true
        [ -f "$tmp/ruby-${ver}/ext/psych/psych.so" ] && \
          cp "$tmp/ruby-${ver}/ext/psych/psych.so" "$HOME/.local/ruby/lib/ruby/3.4.0/x86_64-linux/" 2>/dev/null || true
        [ -d "$tmp/ruby-${ver}/ext/psych/lib" ] && \
          cp -r "$tmp/ruby-${ver}/ext/psych/lib/." "$HOME/.local/ruby/lib/ruby/3.4.0/" 2>/dev/null || true
    fi
    echo -e "${GREEN}[RUBY] $($HOME/.local/ruby/bin/ruby -v)${NC}"
}

instalar_deps_ruby() {
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            as_root apt-get install -y -q build-essential libssl-dev libyaml-dev \
                zlib1g-dev libgmp-dev libffi-dev libreadline-dev git 2>/dev/null
            ;;
        fedora|rhel|centos|rocky|almalinux)
            as_root dnf install -y gcc gcc-c++ make openssl-devel libyaml-devel \
                zlib-devel gmp-devel libffi-devel readline-devel git 2>/dev/null
            ;;
        arch|manjaro)
            as_root pacman -Sy --noconfirm base-devel openssl libyaml zlib gmp libffi readline git
            ;;
        opensuse*|sles)
            as_root zypper install -y gcc gcc-c++ make libopenssl-devel libyaml-devel \
                zlib-devel gmp-devel libffi-devel readline-devel git
            ;;
        *)
            echo -e "${YELLOW}[RUBY] Dependências de compilação: instale manualmente.${NC}"
            ;;
    esac
}

instalar_ruby() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 1. RUBY (microsserviços de boleto e NFS-e SP)${NC}"
    echo -e "${GREEN}=========================================${NC}"

    if [ -x "$HOME/.local/share/mise/installs/ruby" ] 2>/dev/null \
       || mise which ruby >/dev/null 2>&1 \
       || [ -x "$HOME/.local/ruby/bin/ruby" ]; then
        echo -e "${GREEN}[RUBY] Já instalado.${NC}"
    else
        instalar_deps_ruby
        instalar_ruby_mise || instalar_ruby_fonte
    fi

    # bundle do CNAB: SEMPRE fora do repositorio.
    # O caminho do repo tem espaço ("GIT Repos") e isso quebra o Ghostscript
    # na geracao de PDF de boleto (erro /undefinedfilename). O rghost monta a
    # linha de comando do gs por concatenacao, entao espaco = falha.
    local cnab="$PROJECT_DIR/src/main/resources/microservices/boleto-cnab-api"
    if [ -f "$cnab/Gemfile" ]; then
        # mise instala o ruby em ~/.local/share/mise/installs/ruby/<v>/bin e cria
        # shims em ~/.local/share/mise/shims. Sem os shims no PATH, o
        # `ruby -S bundle` acha o interpretador mas nao acha o executavel
        # bundle — e o bundle install falha com "No such file or directory".
        export PATH="$HOME/.local/bin:$HOME/.local/share/mise/shims:$PATH"

        local ruby_bin bundle_bin
        ruby_bin="$(mise which ruby 2>/dev/null || true)"
        [ -n "$ruby_bin" ] || ruby_bin="$HOME/.local/ruby/bin/ruby"
        bundle_bin="$(dirname "$ruby_bin")/bundle"
        [ -x "$bundle_bin" ] || bundle_bin="$(command -v bundle || true)"

        if [ -n "$ruby_bin" ] && [ -n "$bundle_bin" ] && [ -x "$bundle_bin" ]; then
            ( cd "$cnab" \
              && "$bundle_bin" config set --local path "$HOME/.local/share/brasil-saas-gems" >/dev/null 2>&1 \
              && "$bundle_bin" install ) \
              && echo -e "${GREEN}[RUBY] Gems do boleto CNAB instaladas.${NC}" \
              || echo -e "${YELLOW}[RUBY] bundle install falhou; rode de novo.${NC}"
        else
            echo -e "${YELLOW}[RUBY] bundle não encontrado; pulei o CNAB.${NC}"
        fi
    fi
}

# =============================================================================
# 2. CERTIFICADOS
# =============================================================================
# O PostgreSQL daqui autentica por CERTIFICADO DE CLIENTE (sslclientcert),
# nao por senha. A senha do superusuario postgres e CHANGE_ME_VIA_ENV, mas quem entra
# no banco brasil-saas e o usuario 'sa', com certificado.
#
# Para o 'sa' em outra maquina: gera-se um certificado novo COM OS MESMOS
# NOMES (ca.crt, sa.crt, sa.pk8) e aplica-se. Nome igual evita editar a
# configuracao.

gerar_pki_sa() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 2. CERTIFICADOS (PostgreSQL por cliente)${NC}"
    echo -e "${GREEN}=========================================${NC}"

    local pki="$PROJECT_DIR/certs/pki"
    if [ -f "$pki/ca.crt" ] && [ -f "$pki/issued/sa.crt" ] && [ -f "$pki/private/sa.pk8" ]; then
        echo -e "${GREEN}[CERT] PKI já existe no projeto.${NC}"
    else
        as_root apt-get install -y -q easy-rsa >/dev/null 2>&1 || true
        command -v easyrsa >/dev/null 2>&1 || {
            echo -e "${YELLOW}[CERT] easy-rsa ausente. Instale e rode de novo.${NC}"; return 1; }

        echo -e "${GREEN}[CERT] Gerando CA + certificado do 'sa' (nomes padrao)...${NC}"
        as_root bash -c "
            set -e
            umask 077
            export EASYRSA_PKI=\"$pki\"
            easyrsa init-pki >/dev/null
            easyrsa build-ca nopassout >/dev/null
            easyrsa build-client-full sa nopassout >/dev/null
            # chave em PKCS#8: o PostgreSQL nao le PKCS#1 do easy-rsa
            openssl pkcs8 -topk8 -nocrypt -in \"$pki/private/sa.key\" -out \"$pki/private/sa.pk8\"
            chmod 600 \"$pki/private/sa.key\" \"$pki/private/sa.pk8\"
        " || { echo -e "${RED}[CERT] Falha ao gerar a PKI.${NC}"; return 1; }
    fi

    mkdir -p "$PKI_DESTINO"
    cp -r "$pki/." "$PKI_DESTINO/" 2>/dev/null || true
    chmod 600 "$PKI_DESTINO/private/sa.pk8" 2>/dev/null || true
    echo -e "${GREEN}[CERT] Cópia em $PKI_DESTINO${NC}"
}

# Copia a CA e o certificado do cliente para o trust do Linux.
#
# Sem isso, qualquer ferramenta que faca TLS (curl, git, o proprio Java)
# reclama de "certificate is not trusted" ao falar com a maquina.
instalar_trust_linux() {
    echo -e "${YELLOW}[CERT] Instalando no trust do sistema...${NC}"
    local ca="$PKI_DESTINO/ca.crt"
    local client_crt="$PKI_DESTINO/issued/sa.crt"
    [ -f "$ca" ] || { echo -e "${YELLOW}[CERT] ca.crt ausente; pulando trust.${NC}"; return 1; }

    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop)
            as_root cp "$ca" /usr/local/share/ca-certificates/brasil-saas-ca.crt
            as_root cp "$client_crt" /usr/local/share/ca-certificates/brasil-saas-sa.crt
            as_root update-ca-certificates 2>/dev/null | tail -1
            ;;
        fedora|rhel|centos|rocky|almalinux)
            as_root cp "$ca" /etc/pki/ca-trust/source/anchors/brasil-saas-ca.crt
            as_root cp "$client_crt" /etc/pki/ca-trust/source/anchors/brasil-saas-sa.crt
            as_root update-ca-trust extract 2>/dev/null | tail -1
            ;;
        arch|manjaro)
            as_root cp "$ca" /etc/ca-certificates/trust-source/anchors/brasil-saas-ca.crt
            as_root trust extract-compat 2>/dev/null | tail -1
            ;;
        opensuse*|sles)
            as_root cp "$ca" /etc/pki/trust/anchors/brasil-saas-ca.crt
            as_root cp "$client_crt" /etc/pki/trust/anchors/brasil-saas-sa.crt
            as_root update-ca-certificates 2>/dev/null | tail -1
            ;;
        *)
            echo -e "${YELLOW}[CERT] Trust manual: copie $ca para o diretorio de anchors do sistema.${NC}"
            ;;
    esac
    echo -e "${GREEN}[CERT] Trust do sistema atualizado.${NC}"
}

# ==== Windows ====
instalar_docker_windows() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} Windows — Docker Desktop + Ruby${NC}"
    echo -e "${GREEN}=========================================${NC}"

    if command -v docker >/dev/null 2>&1; then
        echo -e "${GREEN}[DOCKER] Já instalado.${NC}"
    elif command -v winget >/dev/null 2>&1; then
        winget install -e --id Docker.DockerDesktop --accept-source-agreements \
            --accept-package-agreements
        echo -e "${YELLOW}[DOCKER] Reinicie o terminal após instalar.${NC}"
    elif command -v choco >/dev/null 2>&1; then
        choco install -y docker-desktop
    else
        echo -e "${RED}[DOCKER] Instale manualmente: https://docs.docker.com/desktop/install/windows-install/${NC}"
    fi

    # Ruby no Windows: sem mise nativo, o caminho oficial é o RubyInstaller
    if command -v ruby >/dev/null 2>&1; then
        echo -e "${GREEN}[RUBY] Já instalado: $(ruby -v)${NC}"
    elif command -v winget >/dev/null 2>&1; then
        # 3.4 com DevKit: o rghost precisa compilar extensão nativa
        winget install -e --id RubyInstallerTeam.RubyInstallerWithDevKit --accept-source-agreements
        echo -e "${YELLOW}[RUBY] Reabra o terminal para o PATH ser atualizado.${NC}"
    else
        echo -e "${YELLOW}[RUBY] Baixe com DevKit: https://rubyinstaller.org/${NC}"
    fi
}

# Equivalente Windows de instalar_trust_linux: gerenciador de certificados.
instalar_trust_windows() {
    echo -e "${YELLOW}[CERT] Importando no Gerenciador de Certificados...${NC}"
    local ca="$PKI_DESTINO/ca.crt"
    local client="$PKI_DESTINO/issued/sa.crt"
    [ -f "$ca" ] || { echo -e "${YELLOW}[CERT] ca.crt ausente.${NC}"; return 1; }

    # Autoridades Raiz Confiáveis: para validar o servidor
    certutil -addstore -f "ROOT" "$ca" >/dev/null 2>&1 && echo -e "${GREEN}[CERT] CA em Raiz Confiáveis.${NC}"
    # Pessoal: para o cliente falar com o servidor usando o certificado
    certutil -addstore -f "MY" "$client" >/dev/null 2>&1 && echo -e "${GREEN}[CERT] sa.crt em Pessoal.${NC}"
    echo -e "${YELLOW}[CERT] Duplo clique em ca.crt > Instalar > Autoridades Raiz Confiáveis também funciona.${NC}"
}

# =============================================================================
# 3. USUÁRIOS COM SUDO
# =============================================================================
# Cada pessoa com sudo precisa de acesso ao Postgres pelo mesmo certificado,
# senao o psql dela vai pedir senha e falhar. Descobre os usuários e instala
# a configuração no perfil de cada um.

configurar_usuarios_sudo() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 3. USUÁRIOS COM SUDO (acesso ao Postgres)${NC}"
    echo -e "${GREEN}=========================================${NC}"

    # quem tem sudo, sem duplicar o usuário atual e sem Nobody
    local users
    users="$(as_root getent group sudo 2>/dev/null | cut -d: -f4 | tr ',' ' ') $(
        as_root getent group wheel 2>/dev/null | cut -d: -f4 | tr ',' ' ')"

    # soma quem tem ALL=(ALL) no sudoers
    local s
    for s in $(awk -F: '/^[^#].*ALL.*ALL/ {print $1}' /etc/sudoers 2>/dev/null); do
        users="$users $s"
    done

    users="$(echo "$users" | tr ' ' '\n' | grep -vE '^$|^(nobody|Nobody)$' | sort -u)"

    if [ -z "$users" ]; then
        echo -e "${YELLOW}[SUDO] Nenhum usuário com sudo encontrado.${NC}"
        return 0
    fi

    local u home
    for u in $users; do
        home="$(getent passwd "$u" | cut -d: -f6)"
        # conta sem home utilizavel (removida, ou de servico) nao tem onde
        # receber a configuracao — sem isso o mkdir falha com Permission denied
        # e o script segue claiming que configurou.
        if [ -z "$home" ] || [ ! -d "$home" ]; then
            echo -e "${YELLOW}[SUDO] $u ignorado: sem diretório home.${NC}"
            continue
        fi
        # Home de outro usuario exige privilegio: o mkdir sem as_root falha
        # com "Permission denied" em /home/<outro>/.local.
        as_root mkdir -p "$home/.local/share/brasil-saas-certs"
        as_root cp -r "$PKI_DESTINO" "$home/.local/share/brasil-saas-certs/" 2>/dev/null || true
        as_root chown -R "$u:$(id -gn "$u")" "$home/.local/share/brasil-saas-certs" 2>/dev/null || true
        as_root chmod 600 "$home/.local/share/brasil-saas-certs/pki/private/sa.pk8" 2>/dev/null || true

        # Configuracao no .profile e no .bashrc (login e nao-login).
        #
        # Vai por arquivo temporario, e nao por heredoc dentro do as_root:
        # o as_root alimenta o prompt do sudo com a senha pelo stdin, e um
        # heredoc no mesmo stdin seria engolido pela senha em vez de chegar
        # ao arquivo de destino.
        local bloco
        bloco="$(mktemp)"
        cat > "$bloco" <<'BLOCO'

# Brasil SaaS ERP - certificado do PostgreSQL
export BRASIL_SAAS_CERTS_DIR="$HOME/.local/share/brasil-saas-certs/pki"
export PGSSLROOTCERT="$BRASIL_SAAS_CERTS_DIR/ca.crt"
export PGSSLCERT="$BRASIL_SAAS_CERTS_DIR/issued/sa.crt"
export PGSSLKEY="$BRASIL_SAAS_CERTS_DIR/private/sa.pk8"
export PGHOST=localhost
export PGPORT=5432
export PGUSER=sa
BLOCO

        for rc in "$home/.profile" "$home/.bashrc"; do
            as_root touch "$rc" 2>/dev/null || continue
            as_root grep -q 'brasil-saas-certs' "$rc" 2>/dev/null && continue
            as_root cp "$rc" "$rc.bc-orig" 2>/dev/null || true
            as_root sh -c "cat '$bloco' >> '$rc'" 2>/dev/null || true
            as_root chown "$u:$(id -gn "$u")" "$rc" 2>/dev/null || true
        done
        rm -f "$bloco"
        echo -e "${GREEN}[SUDO] $u configurado.${NC}"
    done

    cat <<EOF

  Para testar sem gerar variável de ambiente:

    export PGSSLROOTCERT=/caminho/sem/espaco/ca.crt
    export PGSSLCERT=/caminho/sem/espaco/sa.crt
    export PGSSLKEY=/caminho/sem/espaco/sa.key
    psql -h localhost -U sa -d brasil-saas

  Atenção: o caminho NÃO pode ter espaço. O libpq recusa com
  "unexpected spaces found in ...". Se o projeto estiver em pasta com
  espaço, aponte para \$HOME/.local/share/brasil-saas-certs/pki.

EOF
}

# =============================================================================
# 4. BANCO: senha e restore
# =============================================================================
# -----------------------------------------------------------------------------
# Le uma chave do .env sem executar o arquivo.
#
# Nao usa `source`: o .env tem valor com comentario depois
# (JWT_EXPIRATION=86400000  # 24 horas) e o bash nao trata o resto como
# comentario — ele tentaria rodar `hours` como comando. Ler a linha e o
# suficiente.
# -----------------------------------------------------------------------------
ler_env() {
    local arquivo="$1" chave="$2"
    [ -f "$arquivo" ] || return 0
    sed -n -E "s/^${chave}=(.*)$/\1/p" "$arquivo" \
        | tail -1 \
        | sed -E 's/[[:space:]]+#.*$//' \
        | sed -E 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

# -----------------------------------------------------------------------------
# Pergunta a senha ao usuario. Sem eco e com confirmacao.
#
# Sem terminal — instalacao automatizada, CI — nao ha a quem perguntar, e um
# `read` em stdin fechado derrubaria o instalador inteiro. Nesses casos vale o
# que vier na variavel de ambiente; sem ela, a senha padrao, que e o que o
# resto do instalador ja assume.
# -----------------------------------------------------------------------------
pedir_senha() {
    local rotulo="$1" do_ambiente="$2" var="$3"
    # Os internos nao se chamam s1/s2 de proposito: `printf -v` escreve no
    # escopo da funcao, entao um destino chamado s1 colidiria com um local
    # daqui e a senha se perderia em silencio.
    local _leitura1 _leitura2

    if [ -n "$do_ambiente" ]; then
        printf -v "$var" '%s' "$do_ambiente"
        echo -e "${GREEN}      ${var} veio de BRASIL_SAAS_SENHA_*.${NC}"
        return 0
    fi

    if [ ! -t 0 ]; then
        printf -v "$var" '%s' "$SENHA_PADRAO"
        echo -e "${YELLOW}      Sem terminal: ${var} caiu na senha padrao.${NC}"
        return 0
    fi

    while :; do
        read -r -s -p "      ${rotulo}: " _leitura1; echo
        if [ -z "$_leitura1" ]; then
            echo -e "${YELLOW}      senha vazia; tente de novo.${NC}"
            continue
        fi
        read -r -s -p "      ${rotulo} (confirmar): " _leitura2; echo
        if [ "$_leitura1" = "$_leitura2" ]; then
            printf -v "$var" '%s' "$_leitura1"
            return 0
        fi
        echo -e "${YELLOW}      as duas nao conferem; tente de novo.${NC}"
    done
}

# -----------------------------------------------------------------------------
# Grava um valor no .env, criando a chave se ela nao existir.
#
# A chave do Mongo e a URI sao reescritas juntas de proposito: a URI embute a
# senha embutida na URI do Mongo, entao trocar so a senha no banco deixaria
# o arquivo apontando para uma credencial que nao autentica mais.
# -----------------------------------------------------------------------------
gravar_env() {
    local arquivo="$1" chave="$2" valor="$3"

    if grep -qE "^${chave}=" "$arquivo" 2>/dev/null; then
        CHAVE="$chave" VALOR="$valor" python3 -c '
import os, pathlib, re, sys
arquivo = pathlib.Path(sys.argv[1])
chave, valor = os.environ["CHAVE"], os.environ["VALOR"]
texto = arquivo.read_text()
linha = f"{chave}={valor}"
padrao = re.compile(rf"^{re.escape(chave)}=.*$", re.M)
arquivo.write_text(padrao.sub(lambda _: linha, texto, count=1))
' "$arquivo"
    else
        printf '%s=%s
' "$chave" "$valor" >> "$arquivo"
    fi
}

configurar_senha_postgres() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 4. POSTGRESQL — senha do superusuario${NC}"
    echo -e "${GREEN}=========================================${NC}"

    # Manutenção do banco pela senha. A aplicação não usa isto: ela entra
    # como 'sa', por certificado de cliente.
    #
    # Detalhe do pg_hba.conf: "local all postgres peer map=wheelmap" exige que
    # quem executa esteja no grupo wheel. Sem isso, `sudo -u postgres psql`
    # falha com "Peer authentication failed" e a senha nunca é definida.
    if id -nG "$(id -un)" 2>/dev/null | tr ' ' '\n' | grep -qx wheel; then
        :
    elif getent group wheel >/dev/null 2>&1; then
        as_root usermod -aG wheel "$(id -un)" 2>/dev/null \
            && echo -e "${GREEN}[PG] Usuário $(id -un) adicionado ao grupo wheel (pg_hba wheelmap).${NC}" \
            && echo -e "${YELLOW}[PG] Re-login para o grupo ter efeito.${NC}"
    else
        as_root groupadd -r wheel 2>/dev/null || true
        as_root usermod -aG wheel "$(id -un)" 2>/dev/null || true
    fi

    # A senha vai pelo AMBIENTE e o SQL por heredoc. Nao por `-c`: em `ps`,
    # qualquer usuario da maquina ve a linha de comando dos processos alheios,
    # e `ALTER ROLE postgres WITH PASSWORD '...'` apareceria ali. O ambiente
    # so o dono e o root leem.
    #
    # \getenv traz a variavel para o psql, format('%L') escapa a aspa simples
    # do jeito do proprio PostgreSQL, e \gexec executa o resultado. Nenhuma
    # das tres depende de concatenar string no shell.
    local ok=0
    if BRASIL_SAAS_SENHA_PG="$SENHA_PADRAO" psql_admin_root -q postgres >/dev/null 2>&1 <<'SQL'
\getenv senha BRASIL_SAAS_SENHA_PG
SELECT format('ALTER ROLE postgres WITH LOGIN PASSWORD %L', :'senha') \gexec
SQL
    then
        ok=1
    fi

    if [ "$ok" = "1" ]; then
        echo -e "${GREEN}[PG] Senha do superusuario postgres definida.${NC}"
    else
        echo -e "${YELLOW}[PG] Não foi possível definir a senha automaticamente.${NC}"
        echo -e "${YELLOW}      Use a senha do superusuario do Postgres (instalador), ou:${NC}"
        echo -e "${YELLOW}      sudo -u postgres psql -c \"ALTER ROLE postgres WITH PASSWORD '<senha>';\"${NC}"
    fi
}

# =============================================================================
# 5. AS CREDENCIAIS QUE O ERP USA
#
# Sao duas, e nenhuma delas e' a senha do superusuario por acidente:
#
#   - Postgres, no papel 'sa'. O ERP entra no banco pelo certificado de cliente
#     (sslmode=verify-ca), entao no perfil dev a senha nem e' usada. Os perfis
#     hom e prod usam DB_USER/DB_PASSWORD, e ali ela e' obrigatoria.
#
#   - MongoDB, no usuario 'admin'. Aqui a senha e' usada em TODOS os perfis:
#     application-dev.yml monta a URI como
#     mongodb://admin:${MONGODB_PASSWORD}@localhost:27017/brasil-saas
#
# O .env e' escrito no MESMO passo. Banco e arquivo divergentes e' o que produz
# a falha que motivou isto: faltou MONGODB_PASSWORD no .env, a URI caiu no
# padrao CHANGE_ME_VIA_ENV e a autenticacao do Mongo derrubou o servico no boot.
# =============================================================================
configurar_credenciais_aplicacao() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 5. CREDENCIAIS DO ERP (Postgres + MongoDB)${NC}"
    echo -e "${GREEN}=========================================${NC}"

    local senha_pg senha_mongo
    pedir_senha "Senha do Postgres (papel sa)"  "${BRASIL_SAAS_SENHA_PG:-}"   senha_pg
    pedir_senha "Senha do MongoDB (usuario admin)" "${BRASIL_SAAS_SENHA_MONGO:-}" senha_mongo

    # -------------------------------------------------------------------------
    # Postgres: senha do papel 'sa'
    #
    # Quem cria o papel e' o dump, entao numa instalacao nova ele ainda nao
    # existe. Isso nao e' erro: a senha fica registrada no .env e o papel
    # recebe a senha quando o restore rodar.
    # -------------------------------------------------------------------------
    if as_root -u postgres psql -q -tAc \
            "select 1 from pg_roles where rolname='sa'" 2>/dev/null | grep -q 1; then
        # Ambiente + heredoc, nunca -c: ver a nota em configurar_senha_postgres.
        if BRASIL_SAAS_SENHA_PG="$senha_pg" psql_admin_root -q postgres >/dev/null 2>&1 <<'SQL'
\getenv senha BRASIL_SAAS_SENHA_PG
SELECT format('ALTER ROLE sa WITH LOGIN PASSWORD %L', :'senha') \gexec
SQL
        then
            echo -e "${GREEN}[PG] Senha do papel 'sa' definida.${NC}"
        else
            echo -e "${YELLOW}[PG] Nao foi possivel definir a senha do papel 'sa'.${NC}"
            echo -e "${YELLOW}      sudo -u postgres psql -c \"ALTER ROLE sa WITH PASSWORD '<senha>';\"${NC}"
        fi
    else
        echo -e "${YELLOW}[PG] Papel 'sa' ainda nao existe — o dump e' que cria.${NC}"
        echo -e "${YELLOW}      A senha ficou no .env e sera aplicada depois do restore.${NC}"
    fi

    # -------------------------------------------------------------------------
    # MongoDB: usuario 'admin'
    #
    # Duas situacoes, e elas pedem caminhos diferentes:
    #
    #   - Nao ha usuario nenhum ainda: o Mongo abre a porta pela localhost
    #     exception e o admin e' criado com a senha pedida.
    #
    #   - Ja ha usuario: a localhost exception nao vale mais, e trocar a senha
    #     exige autenticar com a senha de AGORA. O instalador nao tem essa
    #     senha, a nao ser que esteja no .env de uma instalacao anterior.
    # -------------------------------------------------------------------------
    local mongo_criou=0
    if ! command -v mongosh >/dev/null 2>&1; then
        echo -e "${YELLOW}[MONGO] mongosh ausente; senha do admin nao aplicada no banco.${NC}"
        echo -e "${YELLOW}       Ficou so no .env.${NC}"
    elif MONGO_SENHA="$senha_mongo" mongosh --quiet --host localhost --eval '
        const senha = process.env.MONGO_SENHA;
        const admin = db.getSiblingDB("admin");
        const jaexiste = admin.getUser("admin");
        if (jaexiste) {
            admin.changeUserPassword("admin", senha);
            print("senha do admin trocada");
        } else {
            admin.createUser({ user: "admin", pwd: senha, roles: [{ role: "root", db: "admin" }] });
            print("usuario admin criado");
        }
    ' >/dev/null 2>&1; then
        mongo_criou=1
        echo -e "${GREEN}[MONGO] Usuario 'admin' pronto (senha pedida).${NC}"
    else
        # Havia usuario e a localhost exception nao ajudou: tenta com a senha
        # que estiver no .env, que e a de agora.
        local mongo_atual
        mongo_atual="$(ler_env "$PROJECT_DIR/.env" MONGODB_PASSWORD)"
        if [ -n "$mongo_atual" ] && [ "$mongo_atual" != "$senha_mongo" ]; then
            local mongo_url
            mongo_url="$(printf '%s' "$mongo_atual" | url_encode)"
            if MONGO_SENHA="$senha_mongo" mongosh --quiet \
                    "mongodb://admin:${mongo_url}@localhost:27017/admin?authSource=admin" \
                    --eval '
                        const senha = process.env.MONGO_SENHA;
                        db.getSiblingDB("admin").changeUserPassword("admin", senha);
                        print("senha do admin trocada");
                    ' >/dev/null 2>&1; then
                mongo_criou=1
                echo -e "${GREEN}[MONGO] Senha do 'admin' trocada.${NC}"
            fi
        fi
        if [ "$mongo_criou" != "1" ]; then
            echo -e "${YELLOW}[MONGO] Ha usuario 'admin' e a senha de agora nao e' conhecida aqui.${NC}"
            echo -e "${YELLOW}       A senha pedida ficou no .env; o banco ainda tem a antiga.${NC}"
            echo -e "${YELLOW}       Troque com:  mongosh --eval 'db.getSiblingDB(\"admin\").changeUserPassword(\"admin\", \"<senha>\")'${NC}"
        fi
    fi

    # -------------------------------------------------------------------------
    # O .env. Mongo precisa de senha E de URI coerente com ela.
    # -------------------------------------------------------------------------
    local env_file="$PROJECT_DIR/.env"
    if [ ! -f "$env_file" ]; then
        if [ -f "$PROJECT_DIR/.env.example" ]; then
            cp "$PROJECT_DIR/.env.example" "$env_file"
            echo -e "${GREEN}[ENV] .env criado a partir do .env.example.${NC}"
        else
            : > "$env_file"
            echo -e "${GREEN}[ENV] .env criado.${NC}"
        fi
        chmod 600 "$env_file"
    fi

    gravar_env "$env_file" DB_USER         sa
    gravar_env "$env_file" DB_PASSWORD     "$senha_pg"
    gravar_env "$env_file" MONGODB_USERNAME admin
    gravar_env "$env_file" MONGODB_PASSWORD "$senha_mongo"
    gravar_env "$env_file" MONGO_PASSWORD  "$senha_mongo"
    gravar_env "$env_file" MONGODB_URI \
        "mongodb://admin:$(printf '%s' "$senha_mongo" | url_encode)@localhost:27017/brasil-saas?authSource=admin"
    echo -e "${GREEN}[ENV] .env atualizado: DB_USER, DB_PASSWORD, MONGODB_* e MONGO_PASSWORD.${NC}"
    echo -e "${YELLOW}[ENV] MONGO_PASSWORD e' apelido do docker-compose; MONGODB_PASSWORD e' o que o ERP le.${NC}"

    # Exporta para o restore, que vem logo depois e precisa da senha do Mongo.
    export MONGODB_USERNAME=admin
    export MONGODB_PASSWORD="$senha_mongo"
}

# Percent-encode de senha para uso dentro de uma URI do Mongo.
url_encode() {
    python3 -c 'import sys, urllib.parse; sys.stdout.write(urllib.parse.quote(sys.stdin.read().rstrip("\n"), safe=""))'
}

# psql como o superusuario postgres, por qualquer um dos caminhos.
#
# O pg_hba deste projeto tem "local all postgres peer map=wheelmap": sem o
# usuario estar no grupo wheel, tanto `sudo -u postgres` quanto
# `su postgres` caem em "Peer authentication failed". O segundo caminho
# (TCP + senha) sempre funciona, e e o que garante o instalador rodar.
psql_admin_root() {
    if as_root -u postgres psql -q -tAc "select 1" >/dev/null 2>&1; then
        as_root -u postgres psql -q -v ON_ERROR_STOP=1 "$@" 2>/dev/null
        return $?
    fi
    if PGPASSWORD="$SENHA_PADRAO" psql -h localhost -U postgres -d postgres \
        -q -tAc "select 1" >/dev/null 2>&1; then
        PGPASSWORD="$SENHA_PADRAO" psql -h localhost -U postgres -d postgres \
            -q -v ON_ERROR_STOP=1 "$@" 2>/dev/null
        return $?
    fi
    return 1
}

# Restaura o dump compactado (parte_dump_01..04 + mongo + schema).
restaurar_banco() {
    echo -e "${GREEN}=========================================${NC}"
    echo -e "${GREEN} 5. BANCO — extração e importação${NC}"
    echo -e "${GREEN}=========================================${NC}"

    # O numero de partes vem do dump, no dump_manifest.env. Ele era 4 fixo aqui
    # e no exportador, em lugares diferentes: um dump de 8 partes-era o
    # restaurador procurando so as quatro primeiras, e o erro era "parte
    # ausente" — que parece arquivo faltando, e nao e'.
    # O padrao e' 8, igual ao exportador. Antes era 4 aqui e 4 la, e o numero
    # da etapa 2 era sobrescrito pelo manifesto quando ele existia — entao o 4
    # deste ponto so aparecia quando o manifesto faltava, que e' quando nao
    # pode faltar.
    local PARTES=8
    if [ -f "$PROJECT_DIR/dump_manifest.env" ]; then
        # shellcheck disable=SC1091
        source "$PROJECT_DIR/dump_manifest.env"
        PARTES="${DUMP_PARTES:-8}"
    fi

    local p01="$PROJECT_DIR/parte_dump_01.zip"
    if [ ! -f "$p01" ]; then
        echo -e "${YELLOW}[BANCO] parte_dump_01.zip não encontrado. Pule o restore.${NC}"
        return 0
    fi
    echo -e "${GREEN}[BANCO] $PARTES partes${NC}"

    local work; work="$(mktemp -d /tmp/brasil-saas-restore.XXXXXX)"
    echo -e "${GREEN}[BANCO] Descompactando as 4 partes em $work${NC}"

    local n src
    # seq -w 01 gera 01, 02, ... 10 — o mesmo formato de dois digitos do nome do
    # arquivo, para 4 partes ou para 40.
    for n in $(seq -w 1 "$PARTES"); do
        local z="$PROJECT_DIR/parte_dump_${n}.zip"
        [ -f "$z" ] || { echo -e "${YELLOW}[BANCO] parte_dump_${n}.zip ausente.${NC}"; rm -rf "$work"; return 1; }
        src="parte$(printf '%02d' $((10#$n - 1)))"
        # -j (junk) e -qq: os pedaços não têm índice central próprio
        unzip -o -j -qq "$z" "$src" -d "$work" || {
            echo -e "${RED}[BANCO] Falha ao extrair $z${NC}"; rm -rf "$work"; return 1; }
        mv "$work/$src" "$work/pedaco_$n"
    done

    cat "$work"/pedaco_0* > "$work/dados.dump"
    if ! pg_restore -l "$work/dados.dump" >/dev/null 2>&1; then
        echo -e "${RED}[BANCO] Dump inválido após junção.${NC}"; rm -rf "$work"; return 1
    fi
    echo -e "${GREEN}[BANCO] Dump remontado: $(du -h "$work/dados.dump" | cut -f1)${NC}"

    # Banco e schema de destino.
    #
    # Estes nomes eram o nome antigo nos dois. O banco e' `brasil-saas` e o schema
    # e' `brasil_saas` — foi o rename que o dono mandou fazer, e este restaurador
    # ficou para tras. A falha era silenciosa e ruim: o dump nao e' recriado aqui
    # (ele esta nos zips, e os zips novos vem do banco `brasil-saas`), o
    # `CREATE DATABASE <nome antigo>` criava um banco a mais, e o `pg_restore`
    # restaurava nele. O resultado era um ERP com o banco vazio e um banco
    # o banco antigo cheio ao lado, sem nenhuma mensagem de erro.
    local banco schema
    banco="brasil-saas"
    schema="brasil_saas"

    # O dump traz "CREATE DATABASE" e "DROP DATABASE" dentro, entao o proprio
    # pg_restore cria o banco com o nome que o DUMP nomeia. Criar o banco aqui
    # antes faz o CREATE do dump falhar — e sem --exit-on-error o restore segue
    # e vai parar no banco da conexao, que e' o postgres. Foi o que aconteceu na
    # primeira rodada: 208 constraints do schema publico brigando no postgres.
    #
    # Por isso o banco e' derrubado antes, e o restore usa --create -d postgres:
    # a conexao comeca no postgres, que existe sempre.
    psql_admin_root psql -q -c "DROP DATABASE IF EXISTS \"$banco\";" 2>/dev/null || true

    # A extensao fica NO schema da aplicacao, e nao em public.
    # As tabelas chamam brasil_saas.uuid_generate_v4(); com a extensao em
    # public, 28 CREATE TABLE falham com "function does not exist" e o
    # restore inteiro fica pela metade. Foi o que aconteceu na primeira
    # tentativa: 282 erros, 134 tabelas de 162.
    psql_admin_root psql -q -d postgres <<SQL 2>/dev/null || true
DROP DATABASE IF EXISTS \"$banco\";
SQL
    psql_admin_root psql -q -d postgres -c \
      "CREATE DATABASE \"$banco\" OWNER sa;" 2>/dev/null || true
    psql_admin_root psql -q -d "$banco" <<SQL 2>/dev/null || true
CREATE SCHEMA IF NOT EXISTS $schema AUTHORIZATION sa;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA $schema;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA $schema;
SQL

    echo -e "${GREEN}[BANCO] Importando (pode demorar)...${NC}"
    # pelo certificado do 'sa', que e' superusuario e entra por cert nos bancos
    # de manutencao. Sem senha: o 'postgres' por TCP cai em scram, e o
    # 'fe_sendauth: no password supplied' que volta e' a mensagem de senha
    # faltando — mas nao e' senha, e' a regra do certificado.
    export PGSSLROOTCERT="$PKI_DESTINO/ca.crt"
    export PGSSLCERT="$PKI_DESTINO/issued/sa.crt"
    # a chave que o libpq le e' a sa.key; a sa.pk8 e' o mesmo par em outro
    # formato, e apontar para ela da "could not load private key file"
    export PGSSLKEY="$PKI_DESTINO/private/sa.key"
    [[ -r "$PGSSLKEY" ]] || export PGSSLKEY="$PKI_DESTINO/private/sa.pk8"
    pg_restore -h localhost -U sa --create -d postgres \
        --no-owner --no-acl --jobs=4 "$work/dados.dump" 2>&1 \
        | grep -v "schema \"$schema\" already exists" | tail -3
    echo -e "${GREEN}[BANCO] Postgres restaurado (por certificado).${NC}"
    unset PGSSLROOTCERT PGSSLCERT PGSSLKEY

    # conferir: a versao do Flyway tem de bater com a do dump
    local versao
    versao="$(psql_admin_root psql -h localhost -U sa -d "$banco" -t -A \
        -c "select coalesce(max(version::numeric),0) from $schema.flyway_schema_history where success;" 2>/dev/null)"
    echo -e "${GREEN}[BANCO] Flyway restaurado até a versão ${versao:-?}${NC}"

    # MongoDB
    # O nome do zip segue o nome do banco no Mongo, que e' MONGODB_DB. Como o
    # banco foi renomeado, existem dois nomes no historico: mongo_<nome antigo>d.zip
    # (o dump de 2026-09-25) e mongo_brasil-saas.zip (o novo). Os dois sao
    # procurados, e o que existir e' restaurado — assim o instalador funciona com
    # o dump antigo e com o novo, sem trocar o script no meio da troca de
    # maquina, que e' justamente quando nao se quer um passo a mais.
    local mdb="${MONGODB_DB:-brasil-saas}"
    local mz=""
    local candidato
    for candidato in "mongo_${mdb}.zip" "mongo_brasil-saas.zip" "mongo_brasil-saas.zip"; do
        if [ -f "$PROJECT_DIR/$candidato" ]; then mz="$PROJECT_DIR/$candidato"; break; fi
    done

    if [ -n "$mz" ] && command -v mongorestore >/dev/null 2>&1; then
        echo -e "${GREEN}[MONGO] Restaurando imagens e documentos de $(basename "$mz")...${NC}"
        local mdir="$work/mongo"; mkdir -p "$mdir"
        unzip -o -qq "$mz" -d "$mdir" || true
        local mpass="${MONGODB_PASSWORD:-$SENHA_PADRAO}"
        local muser="${MONGODB_USERNAME:-admin}"
        mongorestore --uri "mongodb://${muser}:${mpass}@127.0.0.1:27017" \
            --drop "$mdir/$mdb" \
            && echo -e "${GREEN}[MONGO] MongoDB restaurado.${NC}" \
            || echo -e "${YELLOW}[MONGO] Falha no restore do Mongo.${NC}"
    else
        echo -e "${YELLOW}[MONGO] nenhum mongo_*.zip na raiz do projeto, ou mongorestore ausente; pulando.${NC}"
        echo -e "${YELLOW}[MONGO] procurado: mongo_${mdb}.zip, mongo_brasil-saas.zip, mongo_brasil-saas.zip${NC}"
    fi

    rm -rf "$work"
    echo -e "${GREEN}[BANCO] Restore concluído.${NC}"
}

# =============================================================================
# execução
# =============================================================================
case "$(uname -s | tr '[:upper:]' '[:lower:]')" in
    linux|linux-gnu|darwin|cygwin*|msys*|mingw*)
        instalar_ruby
        gerar_pki_sa
        [ "${BRASIL_SAAS_SKIP_TRUST:-0}" = "1" ] || instalar_trust_linux
        configurar_usuarios_sudo
        configurar_senha_postgres
        configurar_credenciais_aplicacao
        [ "${BRASIL_SAAS_SKIP_RESTORE:-0}" = "1" ] || restaurar_banco
        ;;
    *)
        instalar_docker_windows
        instalar_trust_windows
        ;;
esac

cat <<EOF

${BLUE}============================================================${NC}
${BLUE} Etapa 2 concluída${NC}
${BLUE}============================================================${NC}
 Ruby        : CNAB (boleto) e NFS-e SP dependem dele
 Certificados: \$HOME/.local/share/brasil-saas-certs/pki
 Senha pg    : $SENHA_PADRAO  (superusuario postgres; 'sa' usa certificado)
${BLUE}============================================================${NC}

${YELLOW}Certificado de nota (NFS-e São Paulo):${NC}
 A empresa é LTDA, então o A1 é obrigatório — a prefeitura exige
 assinatura de todas as mensagens XML. O arquivo fica FORA do dump e
 tem senha própria, que precisa ser obtida com a AC emissora.
 A tela /fiscal/certificados pede o caminho do .pfx e pergunta se quer
 guardá-lo no banco (coleção "documentos", módulo fiscal).
 O guarda-chuva: a senha NÃO vai para o banco, só o arquivo.

${YELLOW}Layout 2 da NFS-e:${NC}
 Vigência a partir de janeiro (Reforma Tributária). Antes disso o
 webservice de produção ainda não aceita os campos novos — a própria
 prefeitura recomenda manter o layout atual.

EOF
