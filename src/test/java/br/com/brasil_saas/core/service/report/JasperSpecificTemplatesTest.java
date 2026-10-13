package br.com.brasil_saas.core.service.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JasperSpecificTemplatesTest {
    @Test
    void eachManagerialTypeGeneratesAReadablePdf() throws Exception {
        Map<String, Map<String, Object>> samples = new LinkedHashMap<>();
        samples.put("VENDAS", Map.of("numero", "PED-001", "cliente", "Cliente Exemplo", "valor_total", "125.00"));
        samples.put("FINANCEIRO", Map.of("numero_documento", "FIN-001", "descricao", "Título de teste", "valor_original", "99.00"));
        samples.put("PRODUCAO", Map.of("numero", "OP-001", "produto_final", "Produto Exemplo", "quantidade_planejada", "12"));
        samples.put("DRE", Map.of("codigo", "3.1", "descricao", "Receitas de Teste", "resultado", "450.00"));
        samples.put("FLUXO_CAIXA", Map.of("data_projecao", "2026-10-13", "saldo_projetado", "320.00"));

        for (var entry : samples.entrySet()) {
            byte[] bytes = JasperReportRenderer.shared().pdfPorTipo(
                    entry.getKey(), entry.getKey(), "Outubro", "Empresa 42",
                    List.of(entry.getValue()), Map.of("Conferência", "OK"));
            assertTrue(bytes.length > 100, entry.getKey());
            try (var pdf = Loader.loadPDF(bytes)) {
                assertTrue(pdf.getNumberOfPages() >= 1, entry.getKey());
                String text = new PDFTextStripper().getText(pdf);
                assertTrue(text.contains("BrasilCloud ERP"), entry.getKey());
                assertTrue(text.contains("Empresa 42"), entry.getKey());
                assertTrue(text.contains("OK"), entry.getKey());
            }
        }
    }

    @Test
    void rejectsUnrecognizedTemplateNames() {
        assertThrows(IllegalArgumentException.class,
                () -> JasperReportRenderer.shared().pdfPorTipo(
                        "../outro", "Invalido", "", "Empresa 1", List.of(), Map.of()));
    }
}
