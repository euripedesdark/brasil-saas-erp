package br.com.brasil_saas.bi.service.impl;

import java.util.LinkedHashMap;
import br.com.brasil_saas.bi.dto.ReportRequest;
import br.com.brasil_saas.bi.model.Report;
import br.com.brasil_saas.bi.model.ReportParameter;
import br.com.brasil_saas.bi.repository.ReportParameterRepository;
import br.com.brasil_saas.bi.repository.ReportRepository;
import br.com.brasil_saas.bi.service.ReportService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportParameterRepository parameterRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Report create(ReportRequest request, Long empresaId) {
        validateRequest(request);

        Report report = new Report();
        report.setEmpresaId(empresaId);
        applyRequest(report, request);

        Report savedReport = reportRepository.save(report);
        replaceParameters(savedReport, request);
        return savedReport;
    }

    @Override
    @Transactional
    public Report update(Long id, ReportRequest request, Long empresaId) {
        validateRequest(request);

        Report report = reportRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));

        applyRequest(report, request);
        Report savedReport = reportRepository.save(report);
        replaceParameters(savedReport, request);
        return savedReport;
    }

    @Override
    @Transactional(readOnly = true)
    public Report getById(Long id, Long empresaId) {
        return reportRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<Report> listAll(Long empresaId, org.springframework.data.domain.Pageable pageable) {
        return PageResponse.from(reportRepository.findByEmpresaId(empresaId, pageable), item -> item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Report> listByCategory(String category, Long empresaId) {
        return reportRepository.findByEmpresaIdAndCategoryOrderByNameAsc(empresaId, category);
    }

    @Override
    @Transactional
    public void delete(Long id, Long empresaId) {
        Report report = getById(id, empresaId);
        parameterRepository.deleteByReportId(id);
        reportRepository.delete(report);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateReport(Long reportId, Map<String, Object> parameters, String outputType, Long empresaId) {
        Report report = getById(reportId, empresaId);
        String normalizedType = normalizeOutputType(outputType, report.getOutputFormat());
        if (!Set.of("PDF", "XLSX", "EXCEL", "CSV", "HTML").contains(normalizedType)) {
            throw new IllegalArgumentException("Formato de saída inválido: " + outputType);
        }

        Map<String, Object> effectiveParameters = new java.util.LinkedHashMap<>();
        if (parameters != null) effectiveParameters.putAll(parameters);
        effectiveParameters.putIfAbsent("empresaId", empresaId);
        QueryData data = executeQuery(report.getQuerySql(), effectiveParameters);
        return switch (normalizedType) {
            case "PDF" -> generatePdf(data, report);
            case "XLSX", "EXCEL" -> generateExcel(data, report);
            case "CSV" -> generateCsv(data);
            case "HTML" -> generateHtml(data, report);
            default -> throw new IllegalArgumentException("Formato de saida nao suportado: " + outputType);
        };
    }

    @Override
    @Transactional
    public void scheduleReport(Long reportId, Long empresaId) {
        Report report = getById(reportId, empresaId);
        report.setIsScheduled(true);
        reportRepository.save(report);
    }

    @Override
    @Transactional
    public void unscheduleReport(Long reportId, Long empresaId) {
        Report report = getById(reportId, empresaId);
        report.setIsScheduled(false);
        report.setScheduleCron(null);
        report.setScheduleEmail(null);
        reportRepository.save(report);
    }

    private void validateRequest(ReportRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Nome do relatorio e obrigatorio");
        }
        if (request.querySql() == null || request.querySql().isBlank()) {
            throw new IllegalArgumentException("Consulta SQL do relatorio e obrigatoria");
        }
    }

    private void applyRequest(Report report, ReportRequest request) {
        report.setName(request.name().trim());
        report.setDescription(request.description());
        report.setReportType(request.reportType());
        report.setQuerySql(request.querySql().trim());
        report.setTemplatePath(request.templatePath());
        report.setOutputFormat(request.outputFormat() == null ? "PDF" : request.outputFormat().toUpperCase(Locale.ROOT));
        report.setIsScheduled(Boolean.TRUE.equals(request.isScheduled()));
        report.setScheduleCron(request.scheduleCron());
        report.setScheduleEmail(request.scheduleEmail());
        report.setCategory(request.category());
    }

    private void replaceParameters(Report report, ReportRequest request) {
        parameterRepository.deleteByReportId(report.getId());
        if (request.parameters() == null || request.parameters().isEmpty()) {
            report.setParameters(List.of());
            return;
        }

        List<ReportParameter> parameters = request.parameters().stream()
                .map(paramReq -> {
                    ReportParameter param = new ReportParameter();
                    param.setReport(report);
                    param.setName(paramReq.name());
                    param.setLabel(paramReq.label());
                    param.setParameterType(paramReq.parameterType());
                    param.setDefaultValue(paramReq.defaultValue());
                    param.setIsRequired(paramReq.isRequired());
                    param.setSortOrder(paramReq.sortOrder());
                    return param;
                })
                .collect(Collectors.toList());

        parameterRepository.saveAll(parameters);
        report.setParameters(parameters);
    }

    private QueryData executeQuery(String query, Map<String, Object> parameters) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Consulta SQL vazia");
        }

        Map<String, Object> values = parameters == null ? Map.of() : parameters;
        String normalized = query;
        MapSqlParameterSource source = new MapSqlParameterSource();

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            String parameterName = "p_" + key.replaceAll("[^A-Za-z0-9_]", "_");
            normalized = normalized
                    .replace("{{" + key + "}}", ":" + parameterName)
                    .replace(" + key + ", ":" + parameterName)
                    .replace("{" + key + "}", ":" + parameterName)
                    .replace(":" + key, ":" + parameterName);
            source.addValue(parameterName, entry.getValue());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(normalized, source);
        List<String> columns = rows.isEmpty()
                ? extractColumnsFromMetadata(normalized, source)
                : new ArrayList<>(rows.get(0).keySet());

        return new QueryData(columns, rows);
    }

    private List<String> extractColumnsFromMetadata(String sql, MapSqlParameterSource source) {
        return jdbcTemplate.query(sql, source, rs -> {
            var metadata = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnLabel(i));
            }
            return columns;
        });
    }

    private byte[] generatePdf(QueryData data, Report report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 30, 24);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

            Paragraph title = new Paragraph(report.getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            if (report.getDescription() != null && !report.getDescription().isBlank()) {
                Paragraph description = new Paragraph(report.getDescription(), cellFont);
                description.setAlignment(Element.ALIGN_CENTER);
                document.add(description);
            }
            document.add(new Paragraph(" "));

            if (!data.columns().isEmpty()) {
                PdfPTable table = new PdfPTable(data.columns().size());
                table.setWidthPercentage(100);

                for (String column : data.columns()) {
                    PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
                    cell.setPadding(4);
                    table.addCell(cell);
                }

                for (Map<String, Object> row : data.rows()) {
                    for (String column : data.columns()) {
                        PdfPCell cell = new PdfPCell(new Phrase(formatValue(row.get(column)), cellFont));
                        cell.setPadding(3);
                        table.addCell(cell);
                    }
                }
                document.add(table);
            }

            document.add(new Paragraph("Total de registros: " + data.rows().size(), cellFont));
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Erro ao gerar PDF do relatorio", e);
        }
    }

    private byte[] generateExcel(QueryData data, Report report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(report.getName()));
            Row header = sheet.createRow(0);

            for (int i = 0; i < data.columns().size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(data.columns().get(i));
            }

            int rowIndex = 1;
            for (Map<String, Object> row : data.rows()) {
                Row excelRow = sheet.createRow(rowIndex++);
                for (int i = 0; i < data.columns().size(); i++) {
                    setExcelValue(excelRow.createCell(i), row.get(data.columns().get(i)));
                }
            }

            for (int i = 0; i < data.columns().size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao gerar Excel do relatorio", e);
        }
    }

    private byte[] generateCsv(QueryData data) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(data.columns().stream().map(this::csvEscape).collect(Collectors.joining(";"))).append("\r\n");
        for (Map<String, Object> row : data.rows()) {
            csv.append(data.columns().stream()
                    .map(column -> csvEscape(formatValue(row.get(column))))
                    .collect(Collectors.joining(";")))
               .append("\r\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] generateHtml(QueryData data, Report report) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"UTF-8\"><title>")
                .append(escapeHtml(report.getName()))
                .append("</title><style>body{font-family:Arial,sans-serif;margin:24px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:6px;text-align:left}th{font-weight:700}</style></head><body>");
        html.append("<h1>").append(escapeHtml(report.getName())).append("</h1><table><thead><tr>");
        for (String column : data.columns()) {
            html.append("<th>").append(escapeHtml(column)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Map<String, Object> row : data.rows()) {
            html.append("<tr>");
            for (String column : data.columns()) {
                html.append("<td>").append(escapeHtml(formatValue(row.get(column)))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table><p>Total: ").append(data.rows().size()).append("</p></body></html>");
        return html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void setExcelValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(formatValue(value));
        }
    }

    private String normalizeOutputType(String requested, String fallback) {
        String type = requested == null || requested.isBlank() ? fallback : requested;
        if (type == null || type.isBlank()) {
            return "PDF";
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String safeSheetName(String name) {
        String value = name == null || name.isBlank() ? "Relatorio" : name;
        return value.replaceAll("[\\\\/:?*\\[\\]]", "_").substring(0, Math.min(31, value.length()));
    }

    private String formatValue(Object value) {
        if (value == null) return "";
        if (value instanceof TemporalAccessor) return value.toString();
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }

    private String csvEscape(String value) {
        String text = Objects.toString(value, "");
        if (text.contains(";") || text.contains("\"") || text.contains("\r") || text.contains("\n")) {
            return "\""+text.replace("\"", "\"\"")+"\"";
        }
        return text;
    }

    private String escapeHtml(String value) {
        return Objects.toString(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record QueryData(List<String> columns, List<Map<String, Object>> rows) {}
}
