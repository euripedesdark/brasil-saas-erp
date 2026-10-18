# BI no Grafana — dashboards dentro da tela do sistema

O BI do ERP deixou de depender do plugin do Graylog para a **representação
visual**. A tela `/bi` do sistema agora embute um dashboard do **Grafana**,
que é o motor de gráfico usado de verdade aqui.

## Por que Grafana e não o plugin do Graylog

O plugin `brasil-saas-graylog-bi` foi instalado e responde, mas o bundle dele
nao tem nenhum motor de grafico: `svg=0`, `canvas=0`, `recharts=0`, `d3=0`,
`donut=0`, `pie=0`. Ele sabe apenas renderizar numero grande e rotulo. O
objetivo do BI e **visao de operacao com grafico** (serie temporal, composicao,
evolucao), e o Grafana entrega isso nativamente.

O Grafana roda **nativo** (pacote do Fedora), sem Docker, na porta 3000.

## Como fica dentro do sistema

A tela de BI (`/bi`) mostra o dashboard embutido em `<iframe>`, com o mesmo
visual de Painel/Load de sempre. O usuario **nao sai do sistema**: e a mesma
janela, so que a representacao dos dados vem do Grafana.

O ERP **nao faz proxy de nada**. O `iframe` aponta direto para o Grafana
(`http://192.168.2.10:3000/grafana/d/bi-brasil-saas`), que escuta em
`0.0.0.0:3000`. O Grafana autentica pelo mesmo AD, com o mesmo login e senha
do ERP — por isso o painel abre sem tela extra. O endpoint
`/api/bi/grafana/dashboard` do ERP so devolve a URL do painel e a lista de areas
que a pessoa pode ver; nao transporta dado nem consulta o Grafana.

## Login: mesma conta do ERP

O Grafana autentica no **mesmo Active Directory** do ERP, por LDAP:

- `emilly` / `Senha@@2026` funciona no Grafana e no ERP.
- A conta e criada no Grafana no primeiro acesso (auto-signup).
- O perfil no Grafana vem do grupo do AD:

| Grupo no AD | Perfil no Grafana |
|---|---|
| `Domain Admins`, `Administrators`, `GRP_SUPERUSER`, `GRP_ROOT` | Admin |
| `GRP_DIRETORIA` (OU=Diretoria) | Editor |
| `GRP_GERENTE` (OU=Gestao) | Editor |
| `GRP_FINANCEIRO`, `GRP_SA`, `ERP_MODULO_*` | Viewer |

## Filtro por departamento

A regra do dono: **cada um so ve o BI do seu departamento**. Quem esta na
`OU=Diretoria` (e o SUPERUSER / Domain Admins) ve todos os dados.

O filtro e montado pelo ERP, no servidor, a partir dos **grupos do AD** do
usuario da requisicao — e nao a partir do navegador:

- `SUPERUSER` / `GRP_SUPERUSER` / `Domain Admins` -> todas as areas.
- Grupo em `OU=Diretoria` -> todas as areas.
- Grupo em `OU=Gestao` (`GRP_GERENTE`, `GRP_GESTOR`) -> todas as areas.
- Nao sendo isso, o departamento vem do grupo do AD, e o grupo pode ser dos
  dois jeitos que o dominio usa:
  - o tecnico, `ERP_MODULO_<X>` -> area `<X>`;
  - o de negocio, `GRP_<X>` na OU do departamento -> area `<X>`
    (`GRP_VENDEDOR` em `OU=Vendas` vira `vendas`, `GRP_FINANCEIRO` em
    `OU=Financeiro` vira `financeiro`, e assim por diante).

O escopo fechado viaja em `var-area`, e a empresa em `var-empresa`. **Toda**
query do painel filtra pelas duas:

```sql
WHERE empresa_id = CAST('$empresa' AS bigint)
  AND ('financeiro') IN (SELECT unnest(string_to_array('$area', ',')))
```

Quem nao tem a area liberada recebe zero — nao um total, nao um agregado: zero.
Verificado no banco: com `financeiro` liberado a soma e R$ 18.110; com
`var-area=vendas` o painel financeiro devolve 0; com `empresa=99` devolve 0.

Um usuario pode trocar as variaveis na URL, mas **nao ganha nada com isso**:
`$empresa` so amplia a propria empresa, e `$area` nao inventa area que o
servidor nao mandou — o valor que o ERP entrega ja e o teto.

## Sessao do iframe

O dashboard e um `<iframe>`, e iframe nao manda cabecalho `Authorization`:
ele navega. Por isso o login tambem grava o token num cookie
(`BRASIL_SAAS_TOKEN`, `HttpOnly` + `SameSite=Strict`) e o
`JwtAuthenticationFilter` aceita as duas vias — cabecalho e cookie. E o mesmo
JWT, com a mesma validacao e a mesma expiracao; o cookie e outra via de
entrega, nao uma credencial nova. Sem `Secure` de proposito, porque o ERP
tambem roda em http na rede interna e o cookie seria descartado la.


## Arquivos

| Caminho | O que e |
|---|---|
| `deploy/grafana/provisioning/datasources/bi-brasil-saas.yml` | datasource PostgreSQL do banco `brasil_saas_bi` |
| `deploy/grafana/provisioning/dashboards/bi.yaml` | provider de dashboards |
| `deploy/grafana/dashboards/bi-executive.json` | o dashboard "Brasil SaaS — BI Executivo" |
| `deploy/grafana/ldap.toml` | autenticacao no AD |
| `scripts/bi/instalar_grafana_bi.sh` | instala Grafana, role de leitura, datasource e dashboard |

## Dados

O Grafana le **so** o banco `brasil_saas_bi`, pela role `grafana_bi`, que tem
`SELECT` apenas nos schemas `brasil_saas_dl` e `bi_reporting`. Nao ha acesso ao
banco de transacao (`brasil-saas`) nem a qualquer escrita.

Views usadas:

- `brasil_saas_dl.v_bi_financeiro` (275) — a receber, a pagar, vencido
- `brasil_saas_dl.v_bi_vendas` (268) — pedidos por status e valor
- `brasil_saas_dl.v_bi_producao` (116) — ordens por mes
- `brasil_saas_dl.v_bi_servicos` (116) — ordens de servico por mes

O banco BI e repovoado pelo ETL a cada 20–30 minutos
(`brasil-saas-bi-etl.timer`), entao o Grafana mostra a fotografia mais recente.

## Painéis do dashboard

1. **Posicao financeira ao longo do tempo** — serie temporal, a receber x a pagar
2. **A Receber** / **A Pagar** / **Saldo Previsto** / **Vendas Faturadas** — KPIs
3. **Composicao das Vendas** — rosca por tipo
4. **Ordens de Producao por mes** — barras horizontais
5. **Ordens de Servico por mes** e **Valor de Pedidos por mes** — colunas

## Operacao

```bash
# status
systemctl status grafana-server

# recarregar o dashboard apos editar o JSON
systemctl restart grafana-server

# testar o datasource
curl -u grafana_bi:... "postgresql://127.0.0.1:5432/brasil_saas_bi?sslmode=disable"
```
