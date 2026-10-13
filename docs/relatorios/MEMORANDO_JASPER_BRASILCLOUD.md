# Memorando 01 — relatórios Jasper do BrasilCloud ERP

Status: especificação para implementação após resolver o runtime/serviços.
O usuário excluiu a parte fiscal da conversão. Este memorando não declara que
Jasper foi reativado ou que os relatórios foram migrados.

## Inventário observado em main

| Caminho | Implementação atual | Destino |
|---|---|---|
| core/service/report/PdfGeneratorService.generateReport | OpenPDF, tabela de mapas e resumo | Jasper para relatórios gerenciais não fiscais |
| core/service/report/PdfGeneratorService.generateOrdemServicoPdf | OpenPDF, detalhes/itens/totais de OS | Jasper, preservando todos os campos |
| bi/service/impl/RelatorioServiceImpl.gerarPdf | OpenPDF, relatório BI | Jasper após mapear origem segura dos dados |
| bi/service/impl/ReportServiceImpl.generatePdf | OpenPDF, consulta cadastrada | Jasper após substituir consulta livre por catálogo seguro |
| PdfGeneratorService.generateNotaFiscalPdf | OpenPDF fiscal simplificado | Preservar |
| fiscal/nfse/NfsePdfService | OpenPDF fiscal | Preservar |

Os caminhos Java são relativos a `src/main/java/br/com/brasil_saas/`.
PDF/Excel/CSV/HTML, downloads e agendamentos devem conservar contratos e
metadados. O inventário dos geradores PDF não garante cobertura de todos os
exports: rastrear consumidores antes da conversão.

Nenhum JRXML/JXML foi localizado na árvore versionada atual. A extensão Jasper
é `.jrxml`. Preservar os padrões já existentes nos mapas/DTOs, filtros,
empresa, datas e resumos. Templates locais não versionados precisam ser
incorporados ao inventário para afirmar compatibilidade visual exata.

## Identidade visual e memorandos

Nome institucional: BrasilCloud ERP, com nome da empresa emitente separado.
Confirmar alias configurável Brasil SaaS ERP onde ainda é usado pelo sistema.
Preservar a paleta observada no gerador: #2c3e50, #3498db e #f8f9fa.
Cabeçalho: marca do ERP, título, empresa, número/identificador e período.
Corpo: filtros utilizados, colunas alinhadas, grupos/subtotais e resumo.
Rodapé: data de geração, usuário autorizado, página/total e identificação do ERP.
Dinheiro como BigDecimal com escala/moeda; datas e idioma da sessão; acentos.
Usar o logo da empresa via serviço existente, sem URLs externas arbitrárias.

Sankhya é referência de organização visual, não um template obtido nem uma
cópia de marca. A documentação oficial confirma relatórios formatados Jasper
e virtualização, mas não define um único layout visual. Validar amostra renderizada
antes de considerar que ela reproduz o modelo pretendido.

Memorandos operacionais: título MEMORANDO, número, data, empresa, remetente,
destinatário/departamento, assunto, texto, anexos/referências e assinatura.
Gerar a partir de DTOs autorizados; não inventar nome de signatário ou assinatura.
Manter identidade do BrasilCloud e texto configurável por empresa.

Referências: https://ajuda.sankhya.com.br/hc/pt-br/articles/360044597694-Relatórios-Formatados
e https://jasperreports.sourceforge.net/README.html.
