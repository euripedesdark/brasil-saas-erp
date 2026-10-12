package br.com.brasil_saas.fiscal.nfse;

import br.com.brasil_saas.fiscal.model.Nfse;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Locale;
import java.nio.charset.StandardCharsets;

/**
 * Gera o PDF da NFS-e a partir do XML arquivado.
 *
 * <p>A WebService da prefeitura <b>nao</b> tem servico de PDF: o documento
 * sai do portal, em pagina, a partir do numero e do codigo de verificacao.
 * Rastrear essa pagina seria depender de HTML que pode mudar a qualquer
 * momento. Gerar localmente a partir do XML que a propria prefeitura
 * validou e mais estavel e permite reimprimir anos depois.
 *
 * <p>Este PDF e a <b>via de conferencia</b> (dados, valores, QR Code). Quem
 * comprovar a emissao com valor legal e o XML, que fica guardado 5 anos.
 */
@Slf4j
@Service
public class NfsePdfService {

    private static final Font NORMAL = new Font(Font.HELVETICA, 9);
    private static final Font TITULO = new Font(Font.HELVETICA, 13, Font.BOLD);
    private static final Font PEQUENO = new Font(Font.HELVETICA, 7);

    /**
     * @return o PDF, ou {@code null} se nao deu para gerar. Falhar no PDF nao
     *         pode derrubar a emissao: a nota ja foi emitida na prefeitura.
     */
    public byte[] gerar(Nfse nfse, byte[] xmlAssinado) {
        if (nfse == null) {
            return null;
        }
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 30, 30, 40, 40);
            PdfWriter.getInstance(doc, saida);
            doc.open();

            doc.add(espacamento(6));
            Paragraph titulo = new Paragraph("NOTA FISCAL DE SERVICOS ELETRONICA", TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);
            Paragraph sub = new Paragraph("Prefeitura de Sao Paulo - Secretaria da Fazenda", PEQUENO);
            sub.setAlignment(Element.ALIGN_CENTER);
            doc.add(sub);
            doc.add(espacamento(10));

            PdfPTable identificacao = new PdfPTable(4);
            identificacao.setWidthPercentage(100);
            campo(identificacao, "Numero", String.valueOf(nfse.getNumero()), 2);
            campo(identificacao, "Codigo de verificacao", or(nfse.getCodigoVerificacao(), "-"), 2);
            campo(identificacao, "Emissao", nfse.getDataEmissao() == null ? "-"
                    : nfse.getDataEmissao().toLocalDate().toString(), 2);
            campo(identificacao, "Chave nacional", or(nfse.getChaveNotaNacional(), "-"), 2);
            doc.add(identificacao);

            doc.add(espacamento(8));

            PdfPTable servico = new PdfPTable(2);
            servico.setWidthPercentage(100);
            campo(servico, "Codigo do servico (LC 116)", or(nfse.getLc116Codigo(), "-"), 1);
            campo(servico, "Codigo municipal de Sao Paulo",
                    or(nfse.getCodigoTributacaoMunicipal(), "-"), 1);
            campo(servico, "Inscricao municipal do prestador", or(nfse.getCodigoTributacaoMunicipal(), "-"), 1);
            campo(servico, "Serie / numero do RPS",
                    or(nfse.getSerieRps(), "-") + " / " + or(nfse.getNumeroRps(), "-"), 1);
            doc.add(servico);

            doc.add(espacamento(8));

            PdfPTable valores = new PdfPTable(4);
            valores.setWidthPercentage(100);
            campo(valores, "Base de calculo", moeda(nfse.getBaseCalculo()), 2);
            campo(valores, "Valor total", moeda(nfse.getValorTotal()), 2);
            campo(valores, "Aliquota ISS", percentual(nfse.getAliquotaIss()), 2);
            campo(valores, "Valor do ISS", moeda(nfse.getValorIss()), 2);
            doc.add(valores);

            doc.add(espacamento(8));

            Paragraph disc = new Paragraph("Discriminacao dos servicos", new Font(Font.HELVETICA, 9, Font.BOLD));
            doc.add(disc);
            doc.add(new Paragraph(or(discriminacaoDoXml(xmlAssinado), "Ver detalhe no XML da nota."), NORMAL));

            doc.add(espacamento(14));
            Paragraph rodape = new Paragraph(
                    "Documento de conferencia gerado a partir do XML assinado pela Prefeitura de Sao Paulo. "
                            + "Comprovacao fiscal: XML arquivado por 5 anos. Status: " + or(nfse.getStatus(), "-"),
                    PEQUENO);
            rodape.setAlignment(Element.ALIGN_CENTER);
            doc.add(rodape);

            doc.close();
            return saida.toByteArray();

        } catch (Exception e) {
            log.error("Falha ao gerar o PDF da NFS-e {}. A nota segue emitida; "
                    + "o PDF pode ser regerado a partir do XML arquivado. Causa: {}",
                    nfse.getNumero(), e);
            return null;
        }
    }

    // ------------------------------------------------------------------

    private String discriminacaoDoXml(byte[] xml) {
        if (xml == null || xml.length == 0) {
            return null;
        }
        try {
            String texto = new String(xml, StandardCharsets.UTF_8);
            int abre = texto.indexOf("<Discriminacao>");
            if (abre < 0) {
                return null;
            }
            int fecha = texto.indexOf("</Discriminacao>", abre);
            if (fecha < 0) {
                return null;
            }
            return texto.substring(abre + "<Discriminacao>".length(), fecha)
                    .replace("&amp;", "&").replace("&lt;", "<")
                    .replace("&gt;", ">").replace("&quot;", "\"");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void campo(PdfPTable tabela, String titulo, String valor, int colSpan) {
        PdfPCell celula = new PdfPCell();
        celula.setColspan(colSpan);
        celula.setPadding(5);
        celula.addElement(new Phrase(titulo, PEQUENO));
        celula.addElement(new Phrase(valor == null || valor.isBlank() ? "-" : valor, NORMAL));
        tabela.addCell(celula);
    }

    private Paragraph espacamento(int altura) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(altura);
        return p;
    }

    private String or(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private String moeda(java.math.BigDecimal v) {
        return v == null ? "-" : "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", v);
    }

    private String percentual(java.math.BigDecimal v) {
        if (v == null) {
            return "-";
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f%%",
                v.multiply(BigDecimal.valueOf(100)).doubleValue());
    }
}
