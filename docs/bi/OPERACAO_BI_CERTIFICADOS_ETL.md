# BI dedicado: instalacao e validacao

Esta entrega continua o main `e2626848` (que inclui `8d4a6f9`, `71972c9`,
`8c0362b` e `8b92b0e`). As quatro views, o ETL manual, endpoints de resumo,
consultas seletivas, tela React e integracao opcional Graylog ja existiam.
Nao reaplicar `001_reporting_banco_dedicado.sql` nem modificar migrations operacionais
para instalar esta entrega. Os 775 registros informados pelo operador nao foram
recontados neste ambiente: continuam sendo evidencia do servidor, nao deste build.

## Escopo e contrato

- Banco dedicado existente: `brasil_saas_bi`; schemas `bi_reporting` e `brasil_saas_dl`.
- Identidade JDBC: somente `brasil_saas_bi_reader`. Nao utiliza `sa.crt` ou `sa.pk8`.
- O pool nao e um bean `DataSource`: a autoconfiguracao do datasource e do transaction
  manager JPA operacionais permanece. BI desabilitado mantem o modo legado existente.
- Habilitado: valida arquivos TLS, validade/assinatura/CN, TLS real, usuario, banco,
  SELECT nas quatro views e ausencia de permissoes DML no reporting. Configuracao
  incorreta falha na inicializacao; nao cai silenciosamente no banco operacional.
- Resumo e evolucoes da mesma requisicao usam uma transacao propria somente leitura,
  `REPEATABLE READ`, com timeout de 30 segundos; cada consulta tem timeout de 5 segundos.
- Endpoints existentes: `GET /api/bi/resumo` e `GET /api/bi/resumo/{area}`,
  parametros `inicio`, `fim` (ISO, ate 366 dias). Permissoes administrativas preservadas.
  Empresa vem do principal autenticado; SQL nao recebe uma empresa escolhida pelo browser.
- DTO preserva indicadores anteriores; acrescenta `areas[].evolucao[]` com
  `mes` (primeiro dia do mes ISO), `valor`, `unidade`. Meses sem registros nao sao inventados.
- Tela existente exibe quatro dashboards, todos os indicadores, grafico mensal e
  tabela acessivel, estados de carregamento/erro e os quatro idiomas.

| Area | Indicadores e evolucao | Limite dos dados disponiveis |
|---|---|---|
| Financeiro | A receber, a pagar, recebiveis vencidos, saldo previsto; saldo por mes de vencimento | Saldos atuais de titulos positivos nao cancelados. Nao sao receitas/despesas pagas nem fluxo de caixa historico. |
| Producao | Ordens, finalizadas, em processo; ordens por mes de inicio | Finalizadas conforme status atual de ordens iniciadas no periodo. Nao calcula OEE, perdas ou quantidade produzida sem dados na view. |
| Vendas | Pedidos, valor, ticket medio, valor dos pedidos totalmente faturados; valor por mes de emissao | `FATURADO` existe no dominio. Data e de emissao do pedido, nao da NF; `PARCIAL` nao conta como totalmente faturado. Nao e recebimento. |
| Servicos | Ordens abertas, concluidas, valor concluido; conclusoes por mes de fechamento | Abertas usam data de abertura; conclusoes e serie usam fechamento, inclusive ordens abertas antes do periodo. |

## Preflight no servidor PostgreSQL

Execute na raiz do checkout desta entrega, no host PostgreSQL. Nao copie uma CA nova
por cima da existente. O instalador verifica a CA original, CA instalada e
`ssl_ca_file` do cluster, recusando divergencias. Inspeciona banco/role existentes,
TLS e caminho real de `hba_file` pelo PostgreSQL. Nao cria CA, banco, role ou views.

```bash
sudo -u postgres psql -X -w -v ON_ERROR_STOP=1 -d postgres -c 'SHOW ssl; SHOW ssl_ca_file; SHOW hba_file; SHOW port;'
test -s /etc/brasil-saas/pki/ca.crt
test -s certs/pki/ca.crt
sudo test -s certs/pki/private/ca.key
sudo -u postgres psql -X -w -v ON_ERROR_STOP=1 -d brasil_saas_bi -c "SELECT rolname,rolcanlogin,rolsuper,rolbypassrls FROM pg_roles WHERE rolname IN ('brasil_saas_bi_reader','brasil_saas_bi_etl');"
sudo -u postgres psql -X -w -v ON_ERROR_STOP=1 -d brasil_saas_bi -c "SELECT 'financeiro' area,count(*) FROM brasil_saas_dl.v_bi_financeiro UNION ALL SELECT 'producao',count(*) FROM brasil_saas_dl.v_bi_producao UNION ALL SELECT 'vendas',count(*) FROM brasil_saas_dl.v_bi_vendas UNION ALL SELECT 'servicos',count(*) FROM brasil_saas_dl.v_bi_servicos;"
```

Se a PKI original estiver fora de `certs/pki`, use o caminho real em `--pki`.
Certificado existente vencido, par incompleto ou CN incorreto faz o instalador parar;
renovacao/revogacao deve seguir o procedimento da CA, sem sobrescrever chaves existentes.
O usuario Unix `postgres` precisa do acesso administrativo local ja utilizado pelo ETL.
Se a politica peer/map do cluster impedir esse acesso, corrija o acesso administrativo
local de acordo com a politica existente antes de continuar; o script nao muda essa politica.

## Emitir, instalar e provar certificado/JDBC sem senha

Informe o usuario Unix efetivo que executa o ERP (campo `User` do service, ou usuario
do service `--user`). O script entrega a chave com modo **0600**, dono desse usuario.
Os diretorios pais e o driver devem ser legiveis/traversaveis por ele.
Use o JAR PostgreSQL da mesma versao utilizada pelo ERP. Um modo de prepara-lo:

```bash
mvn dependency:copy-dependencies -DincludeArtifactIds=postgresql -DoutputDirectory=target/bi-jdbc
read -r -p 'Usuario Unix efetivo do ERP: ' BI_APP_USER
BI_JDBC_JAR="$(find "$PWD/target/bi-jdbc" -maxdepth 1 -name 'postgresql-*.jar' -print -quit)"
test -n "$BI_APP_USER" && test -s "$BI_JDBC_JAR"
sudo install -d -m 0755 /opt/brasil-saas-bi
sudo install -m 0644 "$BI_JDBC_JAR" /opt/brasil-saas-bi/postgresql.jar
sudo python3 scripts/bi/configurar_certificado_bi.py \
  --pki "$PWD/certs/pki" --easyrsa "$PWD/certs/easyrsa" \
  --app-user "$BI_APP_USER" --jdbc-jar /opt/brasil-saas-bi/postgresql.jar
```

`--pg-port` e `--pg-socket` permitem selecionar o cluster local. HBA autoriza
somente o reader no banco BI, por certificado, em `127.0.0.1` e `::1`. Regras de
rejeicao impedem fallback scram/trust sem certificado nesse banco/usuario/endereco.
As regras anteriores permanecem byte a byte fora do bloco gerenciado.
Cria backup privado quando altera HBA, valida `pg_hba_file_rules`, faz **reload**,
nao restart. Falha de HBA, libpq ou JDBC restaura o HBA anterior. Nao copia a chave da CA.

Arquivos novos em `/etc/brasil-saas/pki`:

- `issued/brasil_saas_bi_reader.crt`: certificado cliente com CN da role.
- `private/brasil_saas_bi_reader.pk8`: PKCS#8 **DER**, JDBC, modo 0600.
- `private/brasil_saas_bi_reader.key`: PEM, libpq/psql, modo 0600.

A prova libpq roda como o usuario do ERP, com `psql -w`, `PGPASSFILE=/dev/null`,
sem herdar `PGPASSWORD` ou `PGSERVICE`. Confirma TLS/role/banco e rejeicao sem
certificado. A prova Java nao fornece propriedade password, consulta as quatro
views e confirma ausencia de escrita do reader.

Repetir a prova JDBC sem emitir nem alterar configuracao:

```bash
sudo -u "$BI_APP_USER" java --class-path /opt/brasil-saas-bi/postgresql.jar \
  /opt/brasil-saas-bi/BiJdbcProbe.java 127.0.0.1 5432 /etc/brasil-saas/pki
```

O instalador **nao habilita BI nem reinicia o ERP**. Somente depois de todas as
provas aprovadas, defina no ambiente do service do ERP:

```ini
BI_DATASOURCE_ENABLED=true
BI_DB_USER=brasil_saas_bi_reader
BI_DB_HOST=localhost
BI_DB_PORT=5432
BRASIL_SAAS_CERTS_DIR=/etc/brasil-saas/pki
```

Mantenha as variaveis operacionais existentes. `BRASIL_SAAS_CERTS_DIR` ja e usada
pelo banco operacional: so redefina se esse ja for seu caminho atual, para nao
mudar a origem dos certificados operacionais. Em testes/staging prefira instalar
os arquivos BI no mesmo diretorio ja configurado e usar `--cert-dir`.
Nao defina senha BI. Reinicie somente o service correto do ERP apos instalar
seu build habitual; nome/escopo do service devem ser os existentes da instalacao.
Confirmar `systemctl status <service>` e health local antes de navegar para BI.

## Automatizar ETL existente

```bash
sudo bash scripts/bi/instalar_etl.sh
sudo systemctl start brasil-saas-bi-etl.service
sudo systemctl status brasil-saas-bi-etl.service --no-pager
sudo systemctl list-timers --all brasil-saas-bi-etl.timer --no-pager
sudo journalctl -u brasil-saas-bi-etl.service -n 80 --no-pager
```

Timer: a cada 15 minutos, persistente, jitter de ate 30 segundos. Uma primeira
execucao do service deve retornar sucesso antes de habilitar o timer. O service
usa `postgres` local, reutilizando a forma de operacao manual ja confirmada.
A role `brasil_saas_bi_etl` e preservada; nao atribuir escrita ao reader.
Para utilizar identidades ETL existentes de menor privilegio, configure
`BI_SOURCE_USER`/`BI_TARGET_USER` e conexao libpq no ambiente do service, apos
validar seus grants/certificados existentes. O instalador nao cria essa identidade
nem amplia grants operacionais. Fonte e executada em transacao **READ ONLY**.

Configuracao: `/etc/brasil-saas/bi-etl.env` (preservada em reinstalacoes).
Pode ajustar origem, usuarios e timeouts. Destino exige `brasil_saas_bi`, diferente
da origem. Trava compartilhada timer/manual: `/run/lock/brasil-saas-bi/etl.lock`,
recriada apos reboot via tmpfiles.d. Sob concorrencia, segunda execucao e ignorada
com mensagem no journal; nao mata a carga em andamento. Lock transacional adicional
no PostgreSQL impede imports concorrentes entre processos que chegaram ao destino.

Extrai as quatro views no mesmo snapshot, carrega staging temporario e publica tudo
na mesma transacao. `DELETE + INSERT` substitui `TRUNCATE`: leitores via MVCC continuam
vendo a carga anterior ate COMMIT, sem `ACCESS EXCLUSIVE`. Autovacuum deve permanecer
ativo no banco BI por causa das versoes mortas. Timeouts limitam bloqueio de escrita
no destino. Nice/IO idle reduzem prioridade; consulta de origem ainda consome IO/CPU,
portanto ajuste frequencia conforme volume. Nenhum DML e executado na origem.

Diagnostico manual com a mesma trava:

```bash
sudo -u postgres bash scripts/bi/sincronizar_reporting.sh
```

Falha na extracao impede importacao. Falha antes do COMMIT preserva a carga anterior.
O journal exibe status, duracao e contagens por area, sem valores individuais.
Nao coloque senhas em argumentos, arquivos versionados ou journal.

## REST e isolamento real

Com o ERP iniciado e BI dedicado habilitado, obtenha duas sessoes administrativas
**de empresas diferentes** pelo login normal. O validador somente faz GET local,
nao segue redirects e nao imprime tokens, IDs ou valores financeiros.

```bash
read -r -s -p 'Token empresa A: ' BI_TOKEN_EMPRESA_A; printf '\n'
read -r -s -p 'Token empresa B: ' BI_TOKEN_EMPRESA_B; printf '\n'
export BI_TOKEN_EMPRESA_A BI_TOKEN_EMPRESA_B
BI_BASE_URL=http://127.0.0.1:8080 BI_INICIO=2026-01-01 BI_FIM=2026-12-31 \
  python3 scripts/bi/validar_api_bi.py
unset BI_TOKEN_EMPRESA_A BI_TOKEN_EMPRESA_B
```

Use a porta HTTP local existente (staging usa sua propria porta); nao muda proxy,
80 ou 443. Prova resumo, selecao das quatro areas, area/periodo invalidos, acesso
anonimo e impossibilidade de selecionar outro tenant via query/header. Se ETL alterar
a carga entre duas chamadas, a comparacao pode falhar: repetir numa janela estavel.
Isso nao substitui confrontar os indicadores com SELECT filtrado por `empresa_id`
no banco BI e com os dados da mesma empresa no ERP. Validacao matematica isolada
com duas empresas tambem consta de `BiResumoServiceTest`.

No navegador: acessar a tela BI ja existente, consultar periodo conhecido, conferir
quatro cards/series e tabela, trocar empresa por login normal e testar quatro idiomas.
Comparar valores com SQL por empresa; confirmar login, operacao comum e health do ERP
para validar compatibilidade com seu datasource operacional.

## Verificacoes reproduziveis

```bash
mvn clean verify
mvn -Dtest=BiResumoServiceTest,BiResumoControllerTest,BiDataSourceConfigTest,GraylogBiControllerTest,ReportSqlParametersTest test
(cd src/main/resources/static/react && npm ci && npm test && npm run build)
python3 scripts/bi/test-operacao-bi.py
python3 scripts/bi/test-certificado-easyrsa.py
python3 scripts/bi/test-validate-staging-bi.py
python3 -m py_compile scripts/bi/configurar_certificado_bi.py scripts/bi/validar_api_bi.py
bash -n scripts/bi/instalar_etl.sh scripts/bi/sincronizar_reporting.sh
systemd-analyze verify scripts/bi/systemd/brasil-saas-bi-etl.service scripts/bi/systemd/brasil-saas-bi-etl.timer
```

`test-operacao-bi.py` usa processos simulados para falhas do psql: prova controle de
fluxo e SQL gerado, **nao** prova rollback num servidor PostgreSQL. Conexao, autenticacao,
views reais, scheduler ativo e inicializacao com dois datasources exigem as provas
no servidor acima. Nao usar o dump de producao nos testes locais.
