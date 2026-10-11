# BI: quatro areas e integracao de consultas

## Escopo inicial

Financeiro, producao, vendas e servicos. O ERP controla autenticacao, empresa,
periodo e consultas permitidas; o datalake e a fonte analitica. O Graylog e a
apresentacao desejada, ainda sem adaptador instalado/validado nesta entrega.
Nao usar o MongoDB de configuracao do Graylog como base dos dados do ERP.

## Implementacao desta etapa

GET /api/bi/resumo?inicio=2026-01-01&fim=2026-01-31 retorna ApiResponse<BiResumo>.
GET /api/bi/resumo/{area} consulta somente a area solicitada (financeiro,
producao, vendas ou servicos); areas invalidas sao recusadas antes da consulta.
Exige JWT e papel ADMIN, SUPERADMIN ou SUPERUSER. A empresa vem exclusivamente
do principal autenticado, inclusive para superuser. Nao aceita empresaId, SQL,
nome de tabela ou consulta arbitraria do cliente. Usuario sem empresa e recusado.
Periodo inclusivo limitado a 366 dias; consultas parametrizadas e somente leitura,
com timeout de 5 segundos por consulta. Erros de banco sao erros, nunca zeros.

V197 adiciona quatro views seletivas em brasil_saas_dl no banco PostgreSQL atual.
Essas views leem dados atuais das tabelas operacionais, excluindo deleted_at.
Nao sao uma copia fisica nem um processo de carga das antigas tabelas ft_*.
Nao cria outro servidor, banco de staging ou copia integral de producao.

- Financeiro: saldos atuais a receber/pagar por vencimento dentro do periodo;
  recebiveis vencidos usam a data atual. Nao representa fluxo de caixa realizado.
- Producao: ordens pela data de inicio (ou criacao), total, finalizadas e em processo.
  data_fim e fechamento real; nao e prazo prometido e nao calcula atraso.
- Vendas: pedidos (exclui orcamentos/cancelados), por emissao, valor e ticket medio.
  Nao denominar faturamento: faturamento parcial e devolucoes exigem outra consulta.
- Servicos: abertas pela abertura no periodo; concluidas e valor pela data de fechamento,
  incluindo ordens abertas antes do periodo. Valor concluido nao e receita recebida.

A tela mostra quatro cartoes, com indicadores secundarios recolhidos e seletor de
periodo. Administracao legada fica recolhida e so e consultada quando aberta.
Rotas e recursos legados permanecem. Rotulos novos nos quatro idiomas.

## Ligacao especifica com Graylog (pendente)

Widgets padrao do Graylog consultam seus indices. Lookup HTTP JSONPath enriquece
mensagens; nao substitui uma fonte SQL de dashboards. Para consultas diretas no
datalake sera necessario um widget/extensao compativel com a versao instalada,
consumindo a API do ERP por um canal autenticado, ou outra integracao acordada.
Nao foi criado input GELF nem copiado o banco para indices, pois o objetivo e
consultar apenas os indicadores selecionados. Nenhuma funcao paga foi contratada.

Antes de implementar o adaptador: identificar versao/URL do Graylog, disponibilidade
de extensoes livres, acesso REST, politica de iframe/CSP e autenticacao. Nao colocar
credenciais em JavaScript, URL de iframe ou repositorio; nao compartilhar token de
administrador entre empresas. Isolamento por empresa deve permanecer no ERP.

## Validacao e implantacao

BiResumoServiceTest executa a migracao em H2 e verifica duas empresas, exclusao,
cancelamento, orcamentos, limites de datas, dados vazios e calculos das quatro areas.
Validacao PostgreSQL e testes do adaptador real dependem do ambiente de staging.
Executar build Maven e npm ci/test/build; revisar a migracao antes de implantar.
Nao atualizar producao enquanto a PR e a integracao real nao forem validadas.

Validacao local desta etapa: 5 testes JUnit/H2 passaram em Java 21; npm ci,
npm test e npm run build passaram. Build Vite alertou sobre bundles grandes.
O Maven completo local nao executou por falta de dependencias no cache; CI sera
usado para confirmar compilacao completa. Build/testes da base #188 passaram,
mas a revisao OpenRouter dessa base retornou HTTP 429 (limite gratuito).

Descoberta no runner dc-erp: graylog-server e graylog-datanode 7.1.9 instalados;
servico graylog-server ativo. Arquivos de configuracao nao legiveis pelo runner.
A primeira consulta HTTP local retornou 403; repetir sem proxy de ambiente antes
de concluir que a API recusa o acesso. Nenhuma credencial foi lida ou impressa.

CI PostgreSQL 18 aplicou V197 e validou as migrations sem reaplica-las. O cenario
posterior de credito tinha expectativa anterior a integracao financeira de devolucoes:
um pedido de R$100 com devolucao de uma de quatro unidades a R$25 passa a saldo R$75,
portanto o credito disponivel de um limite R$1000 e R$925. A expectativa foi alinhada
a esse contrato, com verificacao explicita do saldo apos a devolucao; regra de negocio
nao foi alterada. Nova execucao completa do cenario ainda deve confirmar o resultado.
