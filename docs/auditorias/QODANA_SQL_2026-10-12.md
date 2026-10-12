# Alertas SQL do Qodana — diagnostico e correcao

Auditoria e suite completa: main `8611a157f6770d6a3ede54fd5be1f40f787f3991`.
Base de publicacao: main `8f7469d5eee0826283d25054d70e6d796dedd3d4`,
com os ajustes independentes de CRM e romaneio de producao preservados.
Fonte inspecionada: `9ka6m_JgEEk_82ad83c5-f1e4-493f-b2a1-595de2f92079_qodana.sarif.json`.
O arquivo tem 135 resultados, dos quais **7** sao `SqlSourceToSinkFlow`, nivel warning,
com a mensagem `String, which is used in SQL, can be unsafe`.
Nao sao sete erros de sintaxe do PostgreSQL. Nao equivalem a toda a lista de problemas
possiveis do main atual: o SARIF foi produzido antes de mudancas recentes.

| Local do alerta original | Quantidade | Diagnostico no main auditado |
|---|---:|---|
| `KpiServiceImpl` | 1 | Execucao de SQL configuravel removida na migracao JPA; o calculo atual recusa SELECT/WITH. Isso deve ser confirmado por novo scan, nao por editar o SARIF. |
| `RelatorioServiceImpl` | 1 | Template SQL configuravel permanece; valores sao parametros JDBC. API legada nao sobrescrevia tenant recebido do cliente e validava SQL apenas ao executar. |
| `GerenciadorSqlService` | 5 | Console SQL deliberadamente dinamico. SQL de navegacao/edicao deve validar identificadores; comandos livres sao uma capacidade administrativa existente. |

## Correcao realizada

- Tabelas/views de navegacao, contagem, estrutura e edicao agora sao resolvidas no
  catalogo `information_schema.tables`, exclusivamente no schema `brasil_saas`.
  Nome apenas sintaticamente valido, mas ausente no catalogo, e recusado antes
  da execucao dinamica. Nomes injetados sao recusados antes mesmo da consulta ao catalogo.
- Identificadores validados sao delimitados com aspas duplas. Valores de campos,
  busca, LIMIT e OFFSET seguem como parametros JDBC. Removida a afirmacao incorreta
  de que PostgreSQL nao aceita LIMIT/OFFSET parametrizados.
- Coluna de busca desconhecida agora falha explicitamente em vez de remover o filtro
  e devolver todas as linhas da pagina. Validacao de colunas de edicao preservada.
- Relatorios legados agora sobrescrevem **empresaId e empresa_id** com a empresa
  autenticada, preservando o mapa original recebido. Valores hostis nao viram SQL.
- Validacao de consultas somente leitura tambem ocorre antes de criar/atualizar
  o relatorio, evitando persistir comandos encadeados, escrita ou SQL vazio.
- Testes adicionados para essas fronteiras, incluindo execucao JDBC H2 real com
  valores contendo aspas/comandos e tentativa de trocar a empresa pelo parametro.

Nenhuma inspeção foi desabilitada ou suprimida. Qodana e suas tolerancias permanecem
inalterados; nenhum resultado foi apagado do SARIF. Nao ha migration ou deploy.

## Limites reais

Migrar CRUD operacional para JPA nao elimina SQL de um console SQL nem de templates
SQL administraveis. O analisador pode continuar marcando esses sinks dinamicos.
Eliminar o alerta por exclusao de regra nao seria corrigir seu risco.

A verificacao do parametro empresa protege **o valor reservado**; nao prova que um
SQL livre tenha filtro de empresa em todas as tabelas/joins/subconsultas. A validacao
SELECT/WITH tambem nao constitui sandbox completo contra funcoes SQL privilegiadas.
Portanto a execucao de templates livres continua uma superficie que exige revisao
especifica. Nao e declarada segura apenas por passar nesta validacao.

As permissoes atuais do console (ADMIN/SUPERADMIN/SUPERUSER no controller) e sua
execucao livre foram preservadas para nao alterar silenciosamente a administracao.
A confirmacao de escrita e a rejeicao de comandos encadeados continuam presentes.
Nao se declara que todos os alertas desapareceram: isso exige o novo scan Qodana.

## Validacao

Testes direcionados executados: **10 aprovados**, zero falhas/erros:
`GerenciadorSqlServiceSecurityTest` (5), `RelatorioTenantSqlTest` (1),
`ReportSqlParametersTest` (4). Compilacao e JaCoCo tambem aprovados.

```bash
mvn -Dtest=GerenciadorSqlServiceSecurityTest,RelatorioTenantSqlTest,ReportSqlParametersTest test
mvn clean verify
git diff --check
```

No container, Byte Buddy foi pre-carregado com `-javaagent` junto com JaCoCo, por
indisponibilidade de self-attach. Nenhum teste/scanner foi desabilitado.
Suite completa executada: **BUILD SUCCESS**, 526 testes, zero falhas, zero erros,
um ignorado (BootstrapPostgresTest sem PostgreSQL/Docker). O teste ignorado nao
e declarado aprovado.
A confirmacao de PostgreSQL real e do novo Qodana nao e substituida pelos testes H2.
