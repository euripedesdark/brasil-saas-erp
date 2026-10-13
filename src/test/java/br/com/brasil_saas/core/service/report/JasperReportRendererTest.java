package br.com.brasil_saas.core.service.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JasperReportRendererTest {
    @Test void exportsIdentityRowsAndPaginationWithAccents() throws Exception {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (int i = 0; i < 150; i++) rows.add(Map.of("Descrição", "Produção " + i, "Valor", "R$ 123,45"));
        byte[] bytes = JasperReportRenderer.shared().pdf("Relatório de produção", "Outubro", "Empresa 42",
                rows, List.of("Descrição", "Valor"), Map.of("Total", "R$ 18.517,50"));
        java.nio.file.Files.write(java.nio.file.Path.of("target/jasper-gerencial-preview.pdf"), bytes);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 1);
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("BrasilCloud ERP"), text);
            assertTrue(text.contains("Produção 149"), text);
            assertTrue(text.contains("Empresa 42"), text);
            assertTrue(text.contains("18.517,50"), text);
        }
    }

    @Test void exportsEmptyReportAndMemo() throws Exception {
        byte[] empty = JasperReportRenderer.shared().pdf("Sem dados", "", "Empresa 1", List.of(), List.of(), Map.of());
        try (var pdf = Loader.loadPDF(empty)) { assertEquals(1, pdf.getNumberOfPages()); }
        byte[] memo = JasperReportRenderer.shared().memorando("001", "Manutenção", "Empresa 1", "TI", "Operações", "Ação programada.");
        java.nio.file.Files.write(java.nio.file.Path.of("target/jasper-memorando-preview.pdf"), memo);
        try (var pdf = Loader.loadPDF(memo)) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("MEMORANDO"), text);
            assertTrue(text.contains("Ação programada."), text);
        }
    }

    @Test void inputTextCannotBecomeAReportExpression() throws Exception {
        byte[] bytes = JasperReportRenderer.shared().pdf("Seguro", "", "Empresa 1",
                List.of(Map.of("$P!{SQL}", "SELECT 'x'; -- literal")), List.of("$P!{SQL}"), Map.of());
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(new PDFTextStripper().getText(pdf).contains("SELECT 'x'; -- literal"));
        }
        assertThrows(IllegalArgumentException.class, () -> JasperReportRenderer.shared().pdf("Limite", "", "",
                Collections.nCopies(10001, Map.of()), List.of(), Map.of()));
    }
}
