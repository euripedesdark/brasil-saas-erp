package br.com.brasil_saas.core.service.report;

import org.openpdf.text.*;
import org.openpdf.text.Font;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Serviço para geração de relatórios em PDF
 * Utiliza OpenPDF (fork do iText) para criar documentos PDF profissionais
 */
@Service
public class PdfGeneratorService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 18, Font.BOLD, Color.decode("#2c3e50"));
    private static final Font SUBTITLE_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, Color.decode("#34495e"));
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 12, Font.BOLD, Color.WHITE);
    private static final Font TABLE_HEADER_FONT = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
    private static final Font TABLE_CELL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font FOOTER_FONT = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    
    private static final Color PRIMARY_COLOR = Color.decode("#2c3e50");
    private static final Color SECONDARY_COLOR = Color.decode("#3498db");
    private static final Color TABLE_HEADER_COLOR = Color.decode("#2c3e50");
    private static final Color TABLE_ROW_COLOR = Color.decode("#f8f9fa");

    /**
     * Gera um relatório em PDF
     * @param title Título do relatório
     * @param subtitle Subtítulo do relatório
     * @param empresaId ID da empresa
     * @param empresaNome Nome da empresa
     * @param dataGeracao Data de geração
     * @param dados Dados a serem exibidos na tabela
     * @param columns Colunas da tabela (nomes)
     * @param resumo Resumo/estatísticas
     * @return Byte array do PDF
     */
    public byte[] generateFiscalReport(
            String title, 
            String subtitle,
            Long empresaId,
            String empresaNome,
            LocalDate dataGeracao,
            List<Map<String, Object>> dados,
            List<String> columns,
            Map<String, Object> resumo) throws DocumentException, IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Adiciona cabeçalho
        addHeader(document, title, subtitle, empresaNome, dataGeracao);
        
        // Adiciona tabela de dados
        if (dados != null && !dados.isEmpty()) {
            addDataTable(document, dados, columns);
        }
        
        // Adiciona resumo
        if (resumo != null && !resumo.isEmpty()) {
            addResumo(document, resumo);
        }
        
        // Adiciona rodapé
        addFooter(document, writer);
        
        document.close();
        
        return baos.toByteArray();
    }

    public byte[] generateReport(String title, String subtitle, Long empresaId, String empresaNome,
            LocalDate dataGeracao, List<Map<String, Object>> dados, List<String> columns,
            Map<String, Object> resumo) throws DocumentException, IOException {
        return JasperReportRenderer.shared().pdf(title, subtitle,
                empresaNome == null || empresaNome.isBlank() ? "Empresa " + empresaId : empresaNome,
                dados, columns, resumo);
    }

    /**
     * Gera PDF de Ordem de Serviço
     */
    public byte[] generateOrdemServicoPdf(
            Map<String, Object> osData,
            List<Map<String, Object>> itens,
            String empresaNome) throws DocumentException, IOException {
        
        Map<String, Object> detalhes = new java.util.LinkedHashMap<>(osData);
        return JasperReportRenderer.shared().pdf("Ordem de Serviço", "Nº " + osData.get("numero"),
                empresaNome, itens, List.of("codigo", "descricao", "quantidade", "valorUnitario"), detalhes);
    }

    /**
     * Gera PDF de Nota Fiscal (simplificado)
     */
    public byte[] generateNotaFiscalPdf(
            Map<String, Object> nfData,
            List<Map<String, Object>> itens,
            String empresaNome) throws DocumentException, IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        document.open();
        
        // Cabeçalho
        addHeader(document, "Nota Fiscal", 
                  "Nº " + nfData.get("numero") + " - " + nfData.get("tipo"), 
                  empresaNome, 
                  LocalDate.now());
        
        // Dados da NF
        addNfDetails(document, nfData);
        
        // Itens
        addNfItens(document, itens);
        
        // Totais
        addNfTotais(document, nfData);
        
        // Rodapé
        addFooter(document, writer);
        
        document.close();
        
        return baos.toByteArray();
    }

    private void addHeader(Document document, String title, String subtitle, 
                          String empresaNome, LocalDate dataGeracao) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{1});
        
        // Título
        PdfPCell titleCell = new PdfPCell(new Phrase(title, TITLE_FONT));
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(15);
        titleCell.setBackgroundColor(PRIMARY_COLOR);
        titleCell.setBorder(PdfPCell.NO_BORDER);
        headerTable.addCell(titleCell);
        
        // Subtítulo e empresa
        PdfPCell subtitleCell = new PdfPCell();
        PdfPTable subTable = new PdfPTable(2);
        subTable.setWidthPercentage(100);
        subTable.setWidths(new float[]{3, 1});
        
        subTable.addCell(new Phrase(subtitle, SUBTITLE_FONT));
        subTable.addCell(new Phrase("Empresa: " + empresaNome, TABLE_CELL_FONT));
        
        subTable.addCell(new Phrase("Data: " + dataGeracao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), TABLE_CELL_FONT));
        subTable.addCell(new Phrase("Hora: " + LocalDate.now().toString(), TABLE_CELL_FONT));
        
        subtitleCell.addElement(subTable);
        subtitleCell.setPadding(10);
        subtitleCell.setBorder(PdfPCell.NO_BORDER);
        headerTable.addCell(subtitleCell);
        
        document.add(headerTable);
        
        // Linha separadora
        document.add(new Paragraph(" "));
    }

    private void addDataTable(Document document, List<Map<String, Object>> dados, 
                           List<String> columns) throws DocumentException {
        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        
        // Cabeçalho da tabela
        for (String col : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(col, TABLE_HEADER_FONT));
            cell.setBackgroundColor(TABLE_HEADER_COLOR);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(8);
            table.addCell(cell);
        }
        
        // Dados
        boolean alternate = false;
        for (Map<String, Object> row : dados) {
            for (String col : columns) {
                Object value = row.get(col);
                String displayValue = value != null ? value.toString() : "";
                
                PdfPCell cell = new PdfPCell(new Phrase(displayValue, TABLE_CELL_FONT));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6);
                
                if (alternate) {
                    cell.setBackgroundColor(TABLE_ROW_COLOR);
                }
                
                table.addCell(cell);
            }
            alternate = !alternate;
        }
        
        document.add(table);
    }

    private void addResumo(Document document, Map<String, Object> resumo) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Resumo", SUBTITLE_FONT));
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setWidths(new float[]{3, 1});
        
        for (Map.Entry<String, Object> entry : resumo.entrySet()) {
            table.addCell(new Phrase(entry.getKey(), TABLE_CELL_FONT));
            Object value = entry.getValue();
            String displayValue = formatValue(value);
            PdfPCell valueCell = new PdfPCell(new Phrase(displayValue, TABLE_CELL_FONT));
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(valueCell);
        }
        
        document.add(table);
    }

    private void addOsDetails(Document document, Map<String, Object> osData) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Dados da Ordem de Serviço", SUBTITLE_FONT));
        
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        
        table.addCell(createDetailCell("Cliente:", osData.get("cliente")));
        table.addCell(createDetailCell("Data Início:", osData.get("dataInicio")));
        table.addCell(createDetailCell("Data Fim:", osData.get("dataFim")));
        table.addCell(createDetailCell("Status:", osData.get("status")));
        
        table.addCell(createDetailCell("Descrição:", osData.get("descricao")));
        table.addCell(createDetailCell("Valor Total:", formatCurrency(osData.get("valorTotal"))));
        
        document.add(table);
    }

    private void addOsItens(Document document, List<Map<String, Object>> itens) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Itens", SUBTITLE_FONT));
        
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 1, 1});
        
        // Cabeçalho
        table.addCell(new PdfPCell(new Phrase("Código", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Descrição", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Qtde", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Valor Unit.", TABLE_HEADER_FONT)));
        
        // Itens
        for (Map<String, Object> item : itens) {
            table.addCell(new PdfPCell(new Phrase(item.get("codigo").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.get("descricao").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.get("quantidade").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(formatCurrency(item.get("valorUnitario")), TABLE_CELL_FONT)));
        }
        
        document.add(table);
    }

    private void addOsTotais(Document document, Map<String, Object> osData) throws DocumentException {
        document.add(new Paragraph(" "));
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(30);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        table.addCell(new PdfPCell(new Phrase("Subtotal:", TABLE_CELL_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(osData.get("subtotal")), TABLE_CELL_FONT)));
        
        table.addCell(new PdfPCell(new Phrase("Descontos:", TABLE_CELL_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(osData.get("descontos")), TABLE_CELL_FONT)));
        
        table.addCell(new PdfPCell(new Phrase("Total:", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(osData.get("valorTotal")), TABLE_HEADER_FONT)));
        
        document.add(table);
    }

    private void addNfDetails(Document document, Map<String, Object> nfData) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Dados da Nota Fiscal", SUBTITLE_FONT));
        
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        
        table.addCell(createDetailCell("Cliente:", nfData.get("cliente")));
        table.addCell(createDetailCell("CNPJ/CPF:", nfData.get("cnpjCpf")));
        table.addCell(createDetailCell("Data Emissão:", nfData.get("dataEmissao")));
        table.addCell(createDetailCell("Tipo:", nfData.get("tipo")));
        
        document.add(table);
    }

    private void addNfItens(Document document, List<Map<String, Object>> itens) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Itens", SUBTITLE_FONT));
        
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        
        // Cabeçalho
        table.addCell(new PdfPCell(new Phrase("Código", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Descrição", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("NCM", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Qtde", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase("Valor", TABLE_HEADER_FONT)));
        
        // Itens
        for (Map<String, Object> item : itens) {
            table.addCell(new PdfPCell(new Phrase(item.get("codigo").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.get("descricao").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.get("ncm").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.get("quantidade").toString(), TABLE_CELL_FONT)));
            table.addCell(new PdfPCell(new Phrase(formatCurrency(item.get("valor")), TABLE_CELL_FONT)));
        }
        
        document.add(table);
    }

    private void addNfTotais(Document document, Map<String, Object> nfData) throws DocumentException {
        document.add(new Paragraph(" "));
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(40);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        table.addCell(new PdfPCell(new Phrase("Base ICMS:", TABLE_CELL_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(nfData.get("baseIcms")), TABLE_CELL_FONT)));
        
        table.addCell(new PdfPCell(new Phrase("ICMS:", TABLE_CELL_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(nfData.get("icms")), TABLE_CELL_FONT)));
        
        table.addCell(new PdfPCell(new Phrase("Total:", TABLE_HEADER_FONT)));
        table.addCell(new PdfPCell(new Phrase(formatCurrency(nfData.get("valorTotal")), TABLE_HEADER_FONT)));
        
        document.add(table);
    }

    private PdfPCell createDetailCell(String label, Object value) {
        PdfPCell cell = new PdfPCell();
        PdfPTable innerTable = new PdfPTable(1);
        innerTable.addCell(new Phrase(label, TABLE_CELL_FONT));
        innerTable.addCell(new Phrase(value != null ? value.toString() : "", TABLE_CELL_FONT));
        cell.addElement(innerTable);
        cell.setPadding(5);
        return cell;
    }

    private void addFooter(Document document, PdfWriter writer) throws DocumentException {
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Gerado por Brasil SaaS ERP", FOOTER_FONT));
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(2, java.math.RoundingMode.HALF_UP).toString();
        }
        if (value instanceof Double) {
            return String.format("%.2f", value);
        }
        return value.toString();
    }

    private String formatCurrency(Object value) {
        if (value == null) return "R$ 0,00";
        if (value instanceof BigDecimal) {
            return String.format("R$ %,.2f", ((BigDecimal) value).doubleValue());
        }
        if (value instanceof Double) {
            return String.format("R$ %,.2f", (Double) value);
        }
        return "R$ 0,00";
    }
}
