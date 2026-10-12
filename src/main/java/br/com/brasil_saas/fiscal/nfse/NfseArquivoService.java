package br.com.brasil_saas.fiscal.nfse;

import br.com.brasil_saas.fiscal.model.Nfse;
import br.com.brasil_saas.shared.image.DocumentoArquivo;
import br.com.brasil_saas.shared.image.DocumentoMongoRepository;
import br.com.brasil_saas.shared.service.GenericoDocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Guarda automatica da NFS-e emitida, com prazo por tipo de arquivo.
 *
 * <p>Regra:
 * <ul>
 *   <li><b>XML: 5 anos.</b> Exigencia legal de guarda do documento fiscal
 *       eletronico. E o que a auditoria pede; perder e perder prova.</li>
 *   <li><b>PDF: 60 dias.</b> Serve para reimprimir e conferir o QR Code. Passado
 *       o prazo, o XML mais o numero da nota bastam para comprovar a emissao —
 *       guardar o PDF por 5 anos seria multiplicar o banco sem ganho.</li>
 * </ul>
 *
 * <p>Os dois ficam no MongoDB, na colecao {@code documentos}, e o
 * {@code bc_fis_nfse} guarda so o ponteiro. O Postgres continua com os dados
 * fiscais, que sao os que se consulta em relatorio.
 *
 * <p>Os dois usam {@code tipoEntidade} distinto porque
 * {@code GenericoDocumentoService} apaga o documento anterior do mesmo
 * {@code (empresa, tipoEntidade, entidadeId)}: com o mesmo tipo, salvar o PDF
 * apagaria o XML.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NfseArquivoService {

    public static final String TIPO_XML = "nfse_xml";
    public static final String TIPO_PDF = "nfse_pdf";
    public static final String MODULO = "fiscal";

    private static final int DIAS_XML_PADRAO = 5 * 365;
    private static final int DIAS_PDF_PADRAO = 60;

    private final GenericoDocumentoService documentoService;
    private final DocumentoMongoRepository documentoRepository;

    @Value("${brasil-saas.fiscal.nfse.retencao-dias-xml:1825}")
    private int diasXml = DIAS_XML_PADRAO;

    @Value("${brasil-saas.fiscal.nfse.retencao-dias-pdf:60}")
    private int diasPdf = DIAS_PDF_PADRAO;

    @Value("${brasil-saas.fiscal.nfse.limpeza-ativa:false}")
    private boolean limpezaAtiva;

    /**
     * Guarda o XML assinado da nota.
     *
     * <p>Falha aqui NAO desfaz a emissao: a nota ja foi emitida na prefeitura
     * e o registro fiscal ja existe. Perder o XML e um problema serio de
     * guarda, mas e menos ruim do que reportar a nota como nao emitida e fazer o
     * usuario tentar de novo — o que criaria duplicidade na prefeitura.
     */
    public String guardarXml(Nfse nfse, String xmlAssinado) {
        if (xmlAssinado == null || xmlAssinado.isBlank()) {
            log.error("NFS-e {} sem XML para arquivar. A prefeitura nao devolveu o XML assinado.",
                    nfse.getId());
            return null;
        }
        try {
            DocumentoArquivo doc = documentoService.salvarBytes(
                    nfse.getEmpresaId(), MODULO, TIPO_XML, nfse.getId(),
                    "nfse-" + nfse.getNumero() + ".xml",
                    "application/xml",
                    xmlAssinado.getBytes(StandardCharsets.UTF_8));
            return doc.getId();
        } catch (RuntimeException e) {
            log.error("NFS-e {} emitida mas o XML nao foi arquivado. Prazo de guarda de {} dias "
                    + "esta em risco. Causa: {}", nfse.getNumero(), diasXml, e);
            return null;
        }
    }

    /** Guarda o PDF. Mesma politica da prefeitura: emissao vale mais que arquivo. */
    public String guardarPdf(Nfse nfse, byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            return null;
        }
        try {
            DocumentoArquivo doc = documentoService.salvarBytes(
                    nfse.getEmpresaId(), MODULO, TIPO_PDF, nfse.getId(),
                    "nfse-" + nfse.getNumero() + ".pdf",
                    "application/pdf", pdf);
            return doc.getId();
        } catch (RuntimeException e) {
            log.error("NFS-e {} emitida mas o PDF nao foi arquivado. Causa: {}",
                    nfse.getNumero(), e);
            return null;
        }
    }

    public byte[] lerXml(Nfse nfse) {
        return ler(nfse.getXmlDocumentoId());
    }

    public byte[] lerPdf(Nfse nfse) {
        return ler(nfse.getPdfDocumentoId());
    }

    private byte[] ler(String documentoId) {
        if (documentoId == null || documentoId.isBlank()) {
            return null;
        }
        return documentoRepository.findById(documentoId)
                .map(DocumentoArquivo::getConteudo)
                .orElse(null);
    }

    /**
     * Apaga o que passou do prazo.
     *
     * <p>Desligado por padrao ({@code retencao.limpeza-ativa=false}) porque
     * apagar documento fiscal e acao destrutiva: liga so depois de conferir os
     * prazos, e o log registra o que saiu.
     */
    @Scheduled(cron = "${brasil-saas.fiscal.nfse.limpeza-cron:0 30 3 * * *}")
    public void limparVencidos() {
        if (!limpezaAtiva) {
            log.debug("Limpeza de documentos de NFS-e desativada "
                    + "(brasil-saas.fiscal.nfse.limpeza-ativa=false).");
            return;
        }
        apagar(TIPO_PDF, diasPdf, "PDF");
        apagar(TIPO_XML, diasXml, "XML");
    }

    private void apagar(String tipo, int dias, String rotulo) {
        LocalDateTime limite = LocalDateTime.now().minusDays(dias);
        List<DocumentoArquivo> vencidos = documentoRepository.findByTipoEntidadeAndCriadoEmBefore(tipo, limite);
        if (vencidos.isEmpty()) {
            return;
        }
        documentoRepository.deleteAll(vencidos);
        log.info("Retencao NFS-e: {} documento(s) de {} expirados ({} dia(s) de guarda, anterior a {}). "
                        + "Restam {} de {}.",
                vencidos.size(), rotulo, dias, limite.toLocalDate(),
                documentoRepository.countByTipoEntidade(tipo), tipo);
    }
}
