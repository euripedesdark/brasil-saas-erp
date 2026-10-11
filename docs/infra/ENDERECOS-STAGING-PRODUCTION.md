# Diagnóstico dos endereços públicos de staging e production

Data da investigação: **2026-10-11**. Procedimento somente leitura; não altera DNS, Nginx, portas, bancos nem serviços.

## Evidências verificadas

- [Deploy de production na main](https://github.com/euripedesdark/brasil-saas-erp/actions/runs/38105629097): o serviço dedicado confirmou o JAR do commit `7f4e0cc83a5e73a84ae00605420d1f981b790c17` e `http://127.0.0.1:8080/actuator/health` retornou saudável. **Esse teste interno não comprova acesso público**.
- [Sondagem read-only no servidor](https://github.com/euripedesdark/brasil-saas-erp/actions/runs/38108036797): `nginx` e `brasil-saas-erp` ativos; `brasil-saas-erp-staging` inativo; respostas HTTP 200 em `127.0.0.1:8080`, `127.0.0.1:8081` e no proxy na porta 80. Os caminhos de `staging.env` e do JAR de staging estavam ausentes **ou não acessíveis ao runner**. A porta 8081 responder 200 **não identifica o processo** nem prova que o staging está saudável.
- [Workflow de compilação](https://github.com/euripedesdark/brasil-saas-erp/blob/main/.github/workflows/cd-deploy.yml): compila a branch `staging`, mas só solicita implantação quando o ref é `main`. Um build verde na branch `staging` **não é deploy de staging**.
- [Nginx versionado](https://github.com/euripedesdark/brasil-saas-erp/blob/main/nginx.conf): ERP configurado em HTTP porta 80, com proxy para 127.0.0.1:8080; não publica a aplicação na 8081 e não configura TLS na 443. O próprio repositório documenta que a porta 443 pertence a outro serviço. É necessário checar a configuração do Nginx em execução para saber se houve alterações fora do Git.
- A PR #167 foi incorporada à `main` durante esta investigação. A [configuração atual de staging](https://github.com/euripedesdark/brasil-saas-erp/blob/main/src/main/resources/application-staging.yml) e [seu exemplo de variáveis](https://github.com/euripedesdark/brasil-saas-erp/blob/main/ops/staging/staging.env.example) **reutilizam recursos de produção**. A ausência de migração Flyway no staging não impede escrita de dados da aplicação. Por isso, não iniciar esse staging contra dados de produção para testes destrutivos ou exploratórios.

## Comandos de inspeção sem alterações no host dc-erp

```bash
systemctl is-active nginx brasil-saas-erp brasil-saas-erp-staging || true
sudo ss -ltnp '( sport = :80 or sport = :443 or sport = :8080 or sport = :8081 )'
sudo systemctl status brasil-saas-erp-staging --no-pager --lines=20
sudo nginx -T 2>/dev/null | grep -nE 'listen |server_name |proxy_pass |return 30[18]' || true
sudo test -s /etc/brasil-saas/staging.env && echo 'staging.env presente' || echo 'staging.env ausente/vazio'
sudo test -s /opt/brasil-saas-erp/staging/app.jar && echo 'staging JAR presente' || echo 'staging JAR ausente/vazio'
```

**Nunca imprimir credenciais**, o conteúdo de arquivos `.env` nem argumentos completos de processos contendo segredos. Para a 8081, identificar o processo e confirmar o perfil Spring e as dependências com segurança, sem presumir que o código HTTP 200 é do staging.

## Conferência de DNS e HTTP pelo cliente externo

Substituir `HOST_PRODUCTION` e `HOST_STAGING` pelos hostnames que o usuário realmente abre no navegador. Executar **fora do servidor**:

```bash
getent ahostsv4 HOST_PRODUCTION
getent ahostsv4 HOST_STAGING

curl --noproxy '*' -sS -o /dev/null -w 'production http: %{http_code} IP=%{remote_ip}\n' --connect-timeout 5 --max-time 12 http://HOST_PRODUCTION/login
curl --noproxy '*' -sS -o /dev/null -w 'production https: %{http_code} IP=%{remote_ip}\n' --connect-timeout 5 --max-time 12 https://HOST_PRODUCTION/login
curl --noproxy '*' -sS -o /dev/null -w 'staging http: %{http_code} IP=%{remote_ip}\n' --connect-timeout 5 --max-time 12 http://HOST_STAGING/login
curl --noproxy '*' -sS -o /dev/null -w 'staging https: %{http_code} IP=%{remote_ip}\n' --connect-timeout 5 --max-time 12 https://HOST_STAGING/login
```

Interpretação:

- **DNS não resolve**: conferir registros DNS A/AAAA/CNAME, servidor de nomes e IP público. Branch de GitHub não provisiona domínio.
- **DNS resolve, mas conexão expira**: conferir firewall, NAT, rede e se a porta externa é a destinada ao ERP.
- **HTTP responde, HTTPS falha ou aponta outro site**: conferir o terminador TLS e proxy reais. Não redirecionar nem assumir a posse da 443 sem autorização; ela pertence a outro serviço neste host.
- **Staging abre a tela da produção**: o proxy HTTP default de `nginx.conf` aponta para 8080 independentemente do Host. É preciso primeiro instalar e validar um staging real e **isolado**, depois definir uma rota pública separada e autorizada.
- **HTTP 200 local**: verificar conteúdo, `/login`, identidade do ambiente, autenticação, origem do processo e independência dos dados antes de declarar disponibilidade.

## Pendências que o Git não resolve sozinho

1. Saber **quais são os dois URLs exatos** inseridos no navegador e quem gerencia seus registros DNS.
2. Identificar qual processo está ouvindo a porta local 8081 e confirmar o estado real de staging.
3. Validar se as configurações em execução de Nginx/TLS divergem dos arquivos versionados.
4. Definir isolamento de PostgreSQL, MongoDB, MinIO, RabbitMQ e credenciais **antes de ativar staging para testes**. A configuração mesclada na PR #167 compartilha recursos de produção.
5. Publicar apenas endpoints autorizados e testar os domínios de fora da máquina.

**Sem reiniciar produção, alterar portas 80/443, fazer deploy de staging contra bancos de produção ou concluir que URLs públicos já funcionam.**
