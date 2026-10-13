package br.com.brasil_saas.core.service.report;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.fill.JRFileVirtualizer;
import net.sf.jasperreports.engine.type.*;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;

/** Templates confiaveis do classpath; recebe dados, nunca SQL/Connection/JRXML externo. */
public final class JasperReportRenderer {
    private static final JasperReportRenderer SHARED = new JasperReportRenderer();
    private final Semaphore slots = new Semaphore(2);
    private final Map<Integer, JasperReport> cache = new LinkedHashMap<>(16, .75f, true);
    public static JasperReportRenderer shared() { return SHARED; }

    public byte[] pdf(String title, String subtitle, String empresa, List<Map<String, Object>> rows,
                      List<String> columns, Map<String, Object> summary) {
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        List<String> safeColumns = columns == null ? List.of() : List.copyOf(columns);
        if (safeRows.size() > 10000 || safeColumns.size() > 20) {
            throw new IllegalArgumentException("Relatorio excede limite: 10000 linhas / 20 colunas");
        }
        Map<String, Object> parameters = parameters(title, subtitle, empresa, summary);
        List<Map<String, ?>> data = new ArrayList<>(safeRows.size());
        for (Map<String, Object> row : safeRows) {
            Map<String, Object> values = new HashMap<>();
            for (int i = 0; i < safeColumns.size(); i++) {
                values.put("c" + i, display(row.get(safeColumns.get(i))));
            }
            data.add(values);
        }
        for (int i = 0; i < safeColumns.size(); i++) parameters.put("HEADER_" + i, safeColumns.get(i));
        parameters.put("SUMMARY", parameters.get("SUMMARY") + "\nTotal de registros: " + safeRows.size());
        return export(template(safeColumns.size()), parameters, data);
    }

    public byte[] memorando(String numero, String assunto, String empresa, String remetente,
                            String destinatario, String texto) {
        Map<String, Object> parameters = parameters("Memorando " + display(numero), assunto, empresa, Map.of());
        parameters.put("SUMMARY", "De: " + display(remetente) + "\nPara: " + display(destinatario)
                + "\n\n" + display(texto));
        return export(load("memorando"), parameters, List.of());
    }

    private static Map<String, Object> parameters(String title, String subtitle, String empresa,
                                                  Map<String, Object> summary) {
        Map<String, Object> out = new HashMap<>();
        out.put("TITLE", display(title)); out.put("SUBTITLE", display(subtitle));
        out.put("EMPRESA", display(empresa));
        out.put("GENERATED", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        StringJoiner text = new StringJoiner("\n");
        if (summary != null) summary.forEach((key, value) -> text.add(key + ": " + display(value)));
        out.put("SUMMARY", text.toString());
        return out;
    }

    private synchronized JasperReport template(int count) {
        JasperReport existing = cache.get(count);
        if (existing != null) return existing;
        try {
            JasperDesign design = design("gerencial");
            JRDesignBand header = new JRDesignBand(); header.setHeight(25);
            JRDesignBand detail = new JRDesignBand(); detail.setHeight(23); detail.setSplitType(SplitTypeEnum.STRETCH);
            int width = count == 0 ? design.getColumnWidth() : design.getColumnWidth() / count;
            for (int i = 0; i < count; i++) {
                JRDesignField field = new JRDesignField(); field.setName("c" + i); field.setValueClass(String.class);
                design.addField(field);
                JRDesignParameter parameter = new JRDesignParameter(); parameter.setName("HEADER_" + i);
                parameter.setValueClass(String.class); design.addParameter(parameter);
                int actualWidth = i == count - 1 ? design.getColumnWidth() - i * width : width;
                JRDesignTextField heading = text(i * width, actualWidth, "$P{HEADER_" + i + "}");
                heading.setMode(ModeEnum.OPAQUE); heading.setBackcolor(Color.decode("#2c3e50"));
                heading.setForecolor(Color.WHITE); heading.setBold(true); header.addElement(heading);
                JRDesignTextField cell = text(i * width, actualWidth, "$F{c" + i + "}");
                cell.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
                cell.setMode(ModeEnum.OPAQUE); cell.setBackcolor(Color.decode("#f8f9fa"));
                detail.addElement(cell);
            }
            design.setColumnHeader(header);
            ((JRDesignSection) design.getDetailSection()).addBand(detail);
            JasperReport compiled = JasperCompileManager.compileReport(design);
            if (cache.size() >= 21) cache.remove(cache.keySet().iterator().next());
            cache.put(count, compiled);
            return compiled;
        } catch (JRException error) { throw new IllegalStateException("Erro no template Jasper gerencial", error); }
    }

    private static JRDesignTextField text(int x, int width, String expression) {
        JRDesignTextField field = new JRDesignTextField(); field.setX(x); field.setY(0);
        field.setWidth(width); field.setHeight(23); field.setFontSize(8f);
        field.setExpression(new JRDesignExpression(expression));
        return field;
    }

    private static JasperDesign design(String name) {
        // name e constante interna; nao recebe templatePath fornecido pelo cliente.
        try (InputStream input = JasperReportRenderer.class.getResourceAsStream("/reports/brasilcloud/" + name + ".jrxml")) {
            if (input == null) throw new IllegalStateException("Template Jasper ausente: " + name);
            return JRXmlLoader.load(input);
        } catch (Exception error) { throw new IllegalStateException("Erro ao carregar template Jasper", error); }
    }

    private static JasperReport load(String name) {
        try { return JasperCompileManager.compileReport(design(name)); }
        catch (JRException error) { throw new IllegalStateException("Erro ao compilar template Jasper", error); }
    }

    private byte[] export(JasperReport report, Map<String, Object> parameters, List<Map<String, ?>> data) {
        if (!slots.tryAcquire()) throw new IllegalStateException("Limite de relatorios simultaneos; tente novamente");
        Path temporary = null; JRFileVirtualizer virtualizer = null;
        try {
            temporary = Files.createTempDirectory("brasilcloud-jasper-");
            virtualizer = new JRFileVirtualizer(50, temporary.toString());
            parameters.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
            JasperPrint print = JasperFillManager.fillReport(report, parameters, new JRMapCollectionDataSource(data));
            return JasperPdfBoxExporter.pdf(print);
        } catch (Exception error) { throw new IllegalStateException("Erro ao gerar relatorio Jasper", error); }
        finally {
            if (virtualizer != null) { try { virtualizer.cleanup(); } catch (Exception ignored) {} }
            if (temporary != null) {
                try (var files = Files.walk(temporary)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} });
                } catch (Exception ignored) {}
            }
            slots.release();
        }
    }

    private static String display(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
