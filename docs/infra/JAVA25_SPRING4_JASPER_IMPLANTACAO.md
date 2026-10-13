# Java 25, Spring Boot 4 e Jasper — implementação

## Resultado no código

ERP e Astral usam Java 25 / Boot 4.1.1. Cloud 2025.1.2, AI 2.0.1 e Springdoc
3.1.1 foram alinhados no ERP. O Astral também usa Springdoc 3.1.1.
Flyway fica no gerenciamento do Boot, com starter explícito. Nenhuma migration
histórica foi modificada. Jackson 2 permanece como ponte para integrações que
dependem dele; o HTTP usa o Jackson do Boot 4, com defaults de compatibilidade.

Templates `reports/brasilcloud/gerencial.jrxml` e `memorando.jrxml` são Jasper 7.
PDFs gerenciais, OS e PDFs dos dois serviços BI passam pelo renderer Jasper.
Fiscal continua no gerador anterior. Excel/CSV/HTML mantêm os exportadores
anteriores e contratos existentes. O catálogo customizado ainda contém SQL
cadastrado: isto não equivale a converter todas as consultas do ERP para JPA.

O centro de relatórios ganha um botão Memorando com campos número, assunto,
destinatário e texto. UI traduzida em PT/EN/ES/FR; o template institucional mantém
Português. Remetente é o usuário autenticado; empresa nunca é aceita do cliente.
API: `POST /api/relatorios/memorando/pdf`, com Bearer, retornando PDF.

## Dependências e memória

Jasper 7.0.8, fontes DejaVu e PDFBox 3.0.5/Graphics2D são usados na apresentação.
Não se adiciona Jasper PDF exporter: sua dependência OpenPDF antiga conflita
com o OpenPDF 3 do fiscal. O Jasper calcula layout/páginas; PDFBox recebe os
vetores e uma camada de texto dos mesmos elementos para busca/cópia.
Validação visual e extração de texto cobrem PT-BR e acentos. Glifos não
suportados pela fonte podem aparecer apenas na camada visual.

Cache de layouts compilados limitado a 21 formatos (0–20 colunas); duas
exportações simultâneas, virtualizador de 50 páginas com limpeza em finally,
máximo de 10000 linhas e 500 páginas. Consultas BI têm limite/timeout locais,
sem alterar JdbcTemplate compartilhado. Byte arrays e o PDF final ainda
ocupam heap: limites não significam consumo zero nem economia já medida no host.

A substituição manual de nomes/valores SQL do ReportService foi removida.
Placeholders legados viram parâmetros nomeados, sem interpolar valores.
Teste H2 prova que payload SQL é dado, id e idLong não colidem e empresa do
cliente não substitui tenant autenticado. As consultas administrativas livres
ainda exigem revisão do SQL cadastrado; não declarar todos os alertas Qodana
eliminados sem executar o scanner. Templates são recursos confiáveis do
classpath, sem SQL ou Connection e sem compilação de JRXML externo.

## Linux e serviços

Depois de integrar os PRs e atualizar ambas as cópias locais:

```bash
cd "$HOME/brasil-saas-erp"
sudo bash scripts/linux/upgrade-java25.sh --install
export JAVA_HOME="$(bash scripts/linux/upgrade-java25.sh --print-home)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

O comando completo de atualização, recompilação e deploy dos dois projetos é:

```bash
bash scripts/linux/deploy-erp-astral.sh
```

Ele faz pull de main na branch atual, recusa alterações locais em fontes, guarda
backups, para ambos, compila frontends e executa `mvn clean verify`, copia os JARs
e seleciona Java 25 nas units. Em falha, restaura JARs/drop-ins e o estado ativo
anterior. Arquivos de build do frontend são ignorados na checagem de fontes.
Verificação final de processo ativo não substitui readiness/login.

Para deploy manual: recompilar frontend + `mvn clean verify` dos dois repositórios. Antes de copiar
JARs e reiniciar, selecionar Java 25 nas units existentes:

```bash
sudo env ERP_JAVA_HOME="$JAVA_HOME" bash scripts/linux/upgrade-java25.sh --apply
```

Esse comando preserva os argumentos atuais do ExecStart (incluindo heap,
perfis e caminhos) e escreve drop-ins `90-java25.conf`. Não reinicia serviços.
O comando recusa units que não sejam `java -jar` reconhecível. O Graylog usa
seu runtime próprio, que não é alterado. Os drop-ins anteriores têm backup;
para reverter, restaurá-los/remover apenas o drop-in novo e executar daemon-reload.

Não usar o comando de deploy antigo com JAVA_HOME fixo em Java 21. Um JAR
compilado com release 25 exige runtime 25 ou superior. Preservar backups dos
JARs e configurações antes do deploy e confirmar readiness HTTP após o startup.

## Mongo e IA

Configurações de conexão foram movidas para `spring.mongodb.uri`. Alias legado
`SPRING_DATA_MONGODB_URI` é aceito por fallback; valores/credenciais existentes
continuam dependentes do ambiente. Não desabilitar autenticação Mongo nem criar
outro mongod para contornar erro 13. O consolidado continua exigindo identificar
os dois dbPaths/units no host antes de encerrar qualquer instância.

Spring AI 2 usa `SPRING_AI_MODEL_CHAT=openai` para ativar o modelo. O padrão é
`none`, com fallback local já existente. Chave/base URL vêm do ambiente atual;
não usar chaves reais nos testes nem incluí-las em logs.

## Verificação

Build e testes executados com Temurin 25.0.4.1 e Maven 3.9.11. ERP: 530 testes, zero falhas/erros e um ignorado; clean verify aprovado. Astral: clean verify aprovado, sem testes Java cadastrados. Os testes Jasper
cobrem paginação, texto buscável, acentos, relatório vazio, memorando e entrada
com aparência de SQL. Frontend ERP: npm ci/test/build; Astral: npm ci/build.
Astral não tem testes Java cadastrados; build não demonstra runtime validado.
H2 continua emitindo avisos de DDL PostgreSQL específico nos testes existentes.
Não houve deploy nem acesso ao PostgreSQL/Mongo/Graylog do usuário nesta execução.
