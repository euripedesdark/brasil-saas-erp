package br.com.brasil_saas.bi.service.impl;


import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import br.com.brasil_saas.bi.dto.RelatorioRequest;
import br.com.brasil_saas.bi.model.Relatorio;
import br.com.brasil_saas.bi.repository.RelatorioRepository;
import br.com.brasil_saas.bi.service.RelatorioService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import org.openpdf.text.*;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import br.com.brasil_saas.bi.service.ReportSqlParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

// Import explícito para evitar ambiguidade

@Service
@RequiredArgsConstructor
public class RelatorioServiceImpl implements RelatorioService {

    private final RelatorioRepository relatorioRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Relatorio criar(Long empresaId, RelatorioRequest request) {
        Relatorio relatorio = new Relatorio();
        relatorio.setEmpresaId(empresaId);
        relatorio.setNome(request.nome());
        relatorio.setDescricao(request.descricao());
        relatorio.setTipo(request.tipo());
        relatorio.setCategoria(request.categoria());
        relatorio.setSqlQuery(request.sqlQuery());
        relatorio.setParametros(objectToJson(request.parametros()));
        relatorio.setAtivo(request.ativo());
        relatorio.setAgendado(request.agendado());
        relatorio.setFrequencia(request.frequencia());
        relatorio.setEmailDestinatarios(request.emailDestinatarios());
        relatorio.setFormatoExportacao(request.formatoExportacao());
        relatorio.setDataCriacao(LocalDateTime.now());

        return relatorioRepository.save(relatorio);
    }

    @Override
    @Transactional
    public Relatorio atualizar(Long empresaId, Long id, RelatorioRequest request) {
        Relatorio relatorio = relatorioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));

        if (!relatorio.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio nao pertence a esta empresa");
        }

        relatorio.setNome(request.nome());
        relatorio.setDescricao(request.descricao());
        relatorio.setTipo(request.tipo());
        relatorio.setCategoria(request.categoria());
        relatorio.setSqlQuery(request.sqlQuery());
        relatorio.setParametros(objectToJson(request.parametros()));
        relatorio.setAtivo(request.ativo());
        relatorio.setAgendado(request.agendado());
        relatorio.setFrequencia(request.frequencia());
        relatorio.setEmailDestinatarios(request.emailDestinatarios());
        relatorio.setFormatoExportacao(request.formatoExportacao());

        return relatorioRepository.save(relatorio);
    }

    @Override
    @Transactional(readOnly = true)
    public Relatorio buscarPorId(Long empresaId, Long id) {
        Relatorio relatorio = relatorioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));

        if (!relatorio.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio nao pertence a esta empresa");
        }

        return relatorio;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Relatorio> listarPorEmpresa(Long empresaId) {
        return relatorioRepository.findByEmpresaIdAndAtivoTrue(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Relatorio> listarPorCategoria(Long empresaId, String categoria) {
        return relatorioRepository.findByEmpresaIdAndCategoriaAndAtivo(empresaId, categoria);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Relatorio> listarAgendados(Long empresaId) {
        return relatorioRepository.findByEmpresaIdAndAgendadoTrue(empresaId);
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        Relatorio relatorio = relatorioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relatorio nao encontrado"));

        if (!relatorio.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Relatorio nao pertence a esta empresa");
        }

        relatorioRepository.delete(relatorio);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> executarRelatorio(Long empresaId, Long relatorioId, Map<String, Object> parametros) {
        Relatorio relatorio = buscarPorId(empresaId, relatorioId);

        Map<String, Object> valores = parametros == null ? Map.of() : parametros;
        String sql = ReportSqlParameters.bind(relatorio.getSqlQuery(), valores);
        List<Map<String, Object>> resultados = new NamedParameterJdbcTemplate(jdbcTemplate)
                .queryForList(sql, valores);

        // Calcular resumo
        Map<String, Object> resumo = new HashMap<>();
        if (!resultados.isEmpty()) {
            resumo.put("totalRegistros", resultados.size());
            // Adicionar mais calculos conforme necessario
        }

        Map<String, Object> response = new HashMap<>();
        response.put("relatorio", relatorio.getNome());
        response.put("categoria", relatorio.getCategoria());
        response.put("dados", resultados);
        response.put("resumo", resumo);
        response.put("dataExecucao", LocalDateTime.now());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayOutputStream gerarPdf(Long empresaId, Long relatorioId, Map<String, Object> parametros) {
        Map<String, Object> dados = executarRelatorio(empresaId, relatorioId, parametros);
        Relatorio relatorio = buscarPorId(empresaId, relatorioId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Titulo
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Paragraph title = new Paragraph(relatorio.getNome(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            // Subtitulo
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            Paragraph subtitle = new Paragraph(
                "Categoria: " + relatorio.getCategoria() + " | Data: " + LocalDateTime.now().toLocalDate(),
                subtitleFont
            );
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);

            document.add(new Paragraph(" "));

            // Dados
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultados = (List<Map<String, Object>>) dados.get("dados");
            
            if (resultados != null && !resultados.isEmpty()) {
                // Criar tabela
                PdfPTable table = new PdfPTable(resultados.get(0).size());
                table.setWidthPercentage(100);

                // Cabecalho
                Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
                for (String key : resultados.get(0).keySet()) {
                    PdfPCell cell = new PdfPCell(new Phrase(key, headerFont));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(5);
                    table.addCell(cell);
                }

                // Dados
                Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
                for (Map<String, Object> row : resultados) {
                    for (String key : resultados.get(0).keySet()) {
                        PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(row.get(key)), cellFont));
                        cell.setPadding(5);
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        table.addCell(cell);
                    }
                }

                document.add(table);
            }

            // Resumo
            @SuppressWarnings("unchecked")
            Map<String, Object> resumo = (Map<String, Object>) dados.get("resumo");
            if (resumo != null && !resumo.isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Resumo", new Font(Font.HELVETICA, 14, Font.BOLD)));

                PdfPTable summaryTable = new PdfPTable(2);
                summaryTable.setWidthPercentage(50);

                Font summaryCellFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
                for (Map.Entry<String, Object> entry : resumo.entrySet()) {
                    summaryTable.addCell(new Phrase(entry.getKey(), summaryCellFont));
                    summaryTable.addCell(new Phrase(String.valueOf(entry.getValue()), summaryCellFont));
                }

                document.add(summaryTable);
            }

            document.close();
        } catch (org.openpdf.text.DocumentException e) {
            throw new RuntimeException("Erro ao gerar PDF: " + e.getMessage(), e);
        }

        return baos;
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayOutputStream gerarExcel(Long empresaId, Long relatorioId, Map<String, Object> parametros) {
        Map<String, Object> dados = executarRelatorio(empresaId, relatorioId, parametros);
        Relatorio relatorio = buscarPorId(empresaId, relatorioId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(relatorio.getNome());

            // Cabecalho
            Row headerRow = sheet.createRow(0);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultados = (List<Map<String, Object>>) dados.get("dados");
            
            if (resultados != null && !resultados.isEmpty()) {
                int colIdx = 0;
                for (String key : resultados.get(0).keySet()) {
                    Cell cell = headerRow.createCell(colIdx++);
                    cell.setCellValue(key);
                    cell.setCellStyle(getHeaderStyle(workbook));
                }

                // Dados
                int rowIdx = 1;
                for (Map<String, Object> row : resultados) {
                    Row dataRow = sheet.createRow(rowIdx++);
                    colIdx = 0;
                    for (String key : resultados.get(0).keySet()) {
                        Cell cell = dataRow.createCell(colIdx++);
                        Object value = row.get(key);
                        if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else {
                            cell.setCellValue(String.valueOf(value));
                        }
                    }
                }

                // Ajustar colunas
                for (int i = 0; i < resultados.get(0).size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            workbook.write(baos);
            workbook.close();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel: " + e.getMessage(), e);
        }

        return baos;
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayOutputStream gerarCsv(Long empresaId, Long relatorioId, Map<String, Object> parametros) {
        Map<String, Object> dados = executarRelatorio(empresaId, relatorioId, parametros);
        Relatorio relatorio = buscarPorId(empresaId, relatorioId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultados = (List<Map<String, Object>>) dados.get("dados");
            
            if (resultados != null && !resultados.isEmpty()) {
                // Cabecalho
                StringBuilder sb = new StringBuilder();
                List<String> headers = new ArrayList<>(resultados.get(0).keySet());
                sb.append(String.join(",", headers)).append("\n");

                // Dados
                for (Map<String, Object> row : resultados) {
                    List<String> values = new ArrayList<>();
                    for (String header : headers) {
                        values.add(String.valueOf(row.getOrDefault(header, "")));
                    }
                    sb.append(String.join(",", values)).append("\n");
                }

                baos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar CSV: " + e.getMessage(), e);
        }

        return baos;
    }

    private CellStyle getHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font poiFont = workbook.createFont();
        poiFont.setBold(true);
        poiFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        style.setFont(poiFont);
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.BLUE.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String objectToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
