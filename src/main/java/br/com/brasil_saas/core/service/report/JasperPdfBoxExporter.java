package br.com.brasil_saas.core.service.report;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DFontTextDrawerDefaultFonts;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/** Exporta as paginas calculadas pelo Jasper sem rebaixar OpenPDF dos documentos fiscais. */
final class JasperPdfBoxExporter {
    private JasperPdfBoxExporter() {}

    static byte[] pdf(JasperPrint print) throws Exception {
        if (print.getPages().size() > 500) throw new IllegalArgumentException("Relatorio excede 500 paginas");
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream();
             PdfBoxGraphics2DFontTextDrawerDefaultFonts fonts = new PdfBoxGraphics2DFontTextDrawerDefaultFonts()) {
            document.getDocumentInformation().setProducer("BrasilCloud ERP / JasperReports");
            document.getDocumentInformation().setTitle(print.getName());
            PDType0Font searchableFont;
            try (InputStream font = JasperPdfBoxExporter.class.getResourceAsStream(
                    "/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf")) {
                if (font == null) throw new IllegalStateException("Fonte Jasper ausente");
                searchableFont = PDType0Font.load(document, font);
            }
            for (int index = 0; index < print.getPages().size(); index++) {
                PDPage page = new PDPage(new PDRectangle(print.getPageWidth(), print.getPageHeight()));
                document.addPage(page);
                PdfBoxGraphics2D graphics = new PdfBoxGraphics2D(document, print.getPageWidth(), print.getPageHeight());
                graphics.setFontTextDrawer(fonts);
                try {
                    JRGraphics2DExporter exporter = new JRGraphics2DExporter();
                    exporter.setExporterInput(new SimpleExporterInput(print));
                    SimpleGraphics2DExporterOutput target = new SimpleGraphics2DExporterOutput();
                    target.setGraphics2D(graphics); exporter.setExporterOutput(target);
                    SimpleGraphics2DReportConfiguration configuration = new SimpleGraphics2DReportConfiguration();
                    configuration.setMinimizePrinterJobSize(true); configuration.setPageIndex(index); exporter.setConfiguration(configuration);
                    exporter.exportReport();
                } finally { graphics.dispose(); }
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.drawForm(graphics.getXFormObject());
                    // Jasper Graphics2D usa glifos vetoriais. Manter uma camada de texto
                    // dos mesmos elementos permite busca/copia sem dependencia OpenPDF antiga.
                    textLayer(stream, print.getPages().get(index).getElements(), searchableFont,
                            print.getPageHeight(), 0, 0);
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void textLayer(PDPageContentStream stream, List<JRPrintElement> elements,
                                  PDType0Font font, int pageHeight, int offsetX, int offsetY) throws Exception {
        for (JRPrintElement element : elements) {
            int x = offsetX + element.getX(), y = offsetY + element.getY();
            if (element instanceof JRPrintFrame frame) {
                textLayer(stream, frame.getElements(), font, pageHeight, x, y);
            } else if (element instanceof JRPrintText text && text.getFullText() != null) {
                float size = text.getFontSize();
                int line = 0;
                for (String value : text.getFullText().split("\\R")) {
                    StringBuilder encoded = new StringBuilder();
                    for (int point : value.codePoints().toArray()) {
                        if (point >= 32 && font.hasGlyph(point)) encoded.appendCodePoint(point);
                    }
                    if (!encoded.isEmpty()) {
                        stream.beginText(); stream.setFont(font, size);
                        stream.setRenderingMode(RenderingMode.NEITHER);
                        stream.newLineAtOffset(x, pageHeight - y - size * (1 + line * 1.2f));
                        stream.showText(encoded.toString()); stream.endText();
                    }
                    line++;
                }
            }
        }
    }
}
