# Entrega integrada do BI — 12/10/2026

Base auditada: `main`, commit `e2626848c215c054cc695fd58fa34716ff11de2e`.
Branch de entrega: `feat/bi-certificados-etl-dashboards`.
Commits de configuracao `8d4a6f9`, `71972c9`, `8c0362b`, `8b92b0e` preservados.
Nenhum PR aberto encontrado durante a auditoria inicial. Nenhuma migration operacional,
role, tabela, view ou certificado operacional foi recriado ou modificado.

## Resultado implementado

1. Procedimento idempotente para emitir a identidade reader na Easy-RSA existente,
   comparar CAs, validar CN/par/validade e gerar PKCS#8 DER com permissoes 0600.
   Preserva regras HBA anteriores, adiciona regras estreitas BI antes dos fallbacks,
   valida sintaxe, recarrega PostgreSQL e restaura HBA se a validacao falhar.
2. Provas reais de libpq sem senha, rejeicao sem certificado e JDBC com quatro
   views/ausencia de escrita preparadas no instalador. Nao foram rodadas no servidor.
3. Datasource BI opcional validado antes de ser disponibilizado; sem bean DataSource
   nem transaction manager global que substitua a configuracao operacional.
4. ETL existente reutilizado, com trava timer/manual, snapshot somente leitura na
   origem, publicacao atomica via MVCC, timeout, journal e timer systemd a cada 15 minutos.
5. Resumo e series mensais consultam o mesmo snapshot, com empresa autenticada.
   Indicadores adicionais sao derivados somente de colunas reais das views existentes.
6. Quatro dashboards na tela existente: indicadores, grafico, tabela acessivel,
   filtros e quatro idiomas. Graylog opcional preservado. Bundle canônico atualizado.

Receitas/despesas pagas, faturamento parcial/NF por data de emissao fiscal e OEE nao
sao prometidos: as views atuais nao disponibilizam os dados necessarios. Financeiro
mostra contas/saldos; vendas mostra pedidos e valor de pedidos totalmente faturados.
Os significados e limites ficam descritos na tela e no manual.

## Evidencias executadas neste ambiente

| Verificacao | Resultado observado |
|---|---|
| Java 21, Maven 3.9.9, `mvn clean verify` | **BUILD SUCCESS**; compilacao, empacotamento Boot e JaCoCo concluidos. 522 testes, 0 falhas, 0 erros, 1 ignorado: 521 aprovados. |
| Testes do pacote BI incluidos na suite | 22 aprovados: service (8), controller REST (4), config (3), Graylog (3), parametros SQL (4). |
| Contexto/inicializacao ERP dos testes existentes | Aprovado no ambiente de testes H2; nao prova inicializacao no servidor com dois bancos PostgreSQL. |
| `npm ci`, `npm test`, `npm run build` no frontend canonico | Aprovados; smoke/imports/rotas, i18n e 12 testes da ponte Graylog. Vite gerou bundle. Aviso existente de chunks >500 kB. |
| `python3 scripts/bi/test-operacao-bi.py` | 7 aprovados: HBA idempotente/preservado, ambiente sem senha, trava e falhas ETL. psql simulado, nao e prova de rollback PostgreSQL. |
| `python3 scripts/bi/test-certificado-easyrsa.py` | 2 aprovados com Easy-RSA/OpenSSL reais em PKI descartavel: emissao/reexecucao, CA preservada, CN/par e DER; recusa identidade parcial. |
| `python3 scripts/bi/test-validate-staging-bi.py` | 4 aprovados; comportamento do validador existente preservado. |
| Compilacao `BiJdbcProbe.java` com javac 21 | Aprovada; conexao com servidor nao executada. |
| `py_compile`, `bash -n`, `systemd-analyze verify`, `git diff --check` | Aprovados. |

Para executar Java no container foi extraido JDK 21 e Maven sem modificar o build do
projeto. O ambiente proibe self-attach de agentes: Byte Buddy foi carregado previamente
por `-javaagent` junto com **JaCoCo preservado**, sem desabilitar testes/scanners.
Comando final equivalente ao build completo:

```bash
mvn clean verify \
  '-DargLine=-javaagent:/root/.m2/repository/net/bytebuddy/byte-buddy-agent/1.17.8/byte-buddy-agent-1.17.8.jar -javaagent:/root/.m2/repository/org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar=destfile=target/jacoco.exec'
```

Os caminhos dos agentes sao especificos deste ambiente. Em JDK com attach normal,
utilize o comando padrao `mvn clean verify`. Falhas intermediarias de teste foram
corrigidas antes da execucao final; a suite final completa ficou verde.

O unico teste ignorado e `BootstrapPostgresTest`, condicionado a PostgreSQL/Docker.
Nao e declarado como aprovado. Dados de producao/dumps nao foram utilizados.

## Arquivos alterados

- `src/main/java/br/com/brasil_saas/bi/config/BiDataSourceConfig.java`
- `src/main/java/br/com/brasil_saas/bi/service/BiResumoService.java`
- `src/main/java/br/com/brasil_saas/bi/dto/BiResumo.java`
- `src/main/resources/application.yml` (somente host/porta configuraveis do BI)
- `src/main/resources/static/react/src/components/bi/ResumoBI.jsx`
- `src/main/resources/static/react/src/components/bi/ResumoBI.css`
- `src/main/resources/static/react/src/locales/bi-{pt-BR,en-US,es-ES,fr-FR}.json`
- `src/main/resources/static/dist/index.html` e troca dos bundles index JS/CSS gerados
- `scripts/bi/sincronizar_reporting.sh`
- `scripts/bi/configurar_certificado_bi.py`
- `scripts/bi/BiJdbcProbe.java`
- `scripts/bi/instalar_etl.sh`
- `scripts/bi/systemd/brasil-saas-bi-etl.service`
- `scripts/bi/systemd/brasil-saas-bi-etl.timer`
- `scripts/bi/validar_api_bi.py`
- `scripts/bi/test-operacao-bi.py`
- `scripts/bi/test-certificado-easyrsa.py`
- `src/test/java/br/com/brasil_saas/bi/service/BiResumoServiceTest.java`
- `src/test/java/br/com/brasil_saas/bi/controller/BiResumoControllerTest.java`
- `src/test/java/br/com/brasil_saas/bi/config/BiDataSourceConfigTest.java`
- `docs/bi/OPERACAO_BI_CERTIFICADOS_ETL.md`
- `docs/bi/RELATORIO_ENTREGA_2026-10-12.md`

## Pendencias reais para ativacao

Sem acesso a sessao do servidor, ainda precisam ser executados os comandos exatos do
[manual de operacao](OPERACAO_BI_CERTIFICADOS_ETL.md):

- Confirmar PKI original/CA do cluster, usuario Unix do ERP e certificados BI reais.
- Aplicar instalador e provar libpq + JDBC sem senha, quatro views e grants.
- Instalar service/timer, observar execucao real no journal e apos agendamento/reboot.
- Habilitar explicitamente BI somente apos provas aprovadas e instalar o build pelo
  procedimento existente. Verificar health, login e operacao comum com datasource sa.
- Rodar validador REST com duas empresas reais e confrontar indicadores com SQL.
- Verificar visualmente a tela no navegador autenticado e os quatro idiomas.

Datasource permanece **desabilitado por padrao**. Codigo pronto e testes locais
aprovados nao significam instalacao/ativacao no servidor. Nao houve merge ou deploy.
