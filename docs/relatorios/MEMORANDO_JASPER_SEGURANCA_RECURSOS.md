# Memorando 02 — dados seguros e custo dos relatórios

## Segurança

Jasper é o motor de apresentação; não saneia consultas montadas com entradas.
O caminho gerencial `core/service/RelatorioService` já consulta repositórios
JPQL com tenant parametrizado. Preservar essa origem.
Os dois serviços BI ainda aceitam SQL cadastrado e usam ReportSqlParameters;
parametrização dos valores não transforma SQL arbitrário em um catálogo seguro.

Implementar catálogo de relatórios/datasets com identificadores autorizados e
provedores JPA/DTO. Receber filtros tipados; tenant vem da identidade autenticada.
Entregar ao Jasper `JRBeanCollectionDataSource`/`JRMapCollectionDataSource`:
sem Connection JDBC e sem SQL embutido em JRXML. Não usar `$P!{}` para SQL.
Não aceitar upload/compilação de JRXML de usuário: expressões são código Java.
Templates/subreports são recursos confiáveis do classpath com identificadores
permitidos; negar path traversal, URLs, scripts e carregamento arbitrário.

No controller gerencial há fallback de empresaId vindo de filtros quando o
principal está ausente: revisar autenticação e negar requisição sem identidade
antes da migração; evitar transportar token em URL.
Testar isolamento entre duas empresas, autorização e filtros maliciosos.
Não afirmar que todos os alertas Qodana desapareceram sem novo scan/SARIF.

## Memória e compilação

Jasper 7 altera formato de JRXML/JRTX e compatibilidade de `.jasper`; converter
templates com ferramenta compatível e recompilar. Reativar dependências apenas
após confirmar release, módulos PDF/fontes e dependências efetivas/scanner.
Não considerar toda versão 7 automaticamente livre de vulnerabilidades.

Compilar templates confiáveis no build; cache limitado de templates compilados.
Limitar registros, páginas, duração, concorrência e filas por usuário/empresa.
Para grandes impressões usar virtualizador Jasper com diretório temporário
privado por execução, quota, limpeza em finally e monitoramento de espaço.
Virtualização limita páginas em memória, não a lista inteira de dados.
Usar paginação/stream controlado no provedor e exportar para stream quando o
contrato permitir; byte[] mantém o PDF completo em heap.

Validar relatório vazio, acentos, valores nulos, moeda, totais, múltiplas páginas,
OS completa, cancelamento, erro e concorrência. Registrar heap/RSS/tempo/bytes
antes e depois. Não mudar fiscal nem prometer economia sem medição.

Referência: https://jasperreports.sourceforge.net/README.html
