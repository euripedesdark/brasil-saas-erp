package br.com.brasil_saas.shared.service;

import br.com.brasil_saas.shared.image.DocumentoArquivo;
import br.com.brasil_saas.shared.image.DocumentoMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Armazenamento de documentos no MongoDB com passagem obrigatoria por staging.
 *
 * Diferente de {@link GenericoImagemService}, que mantem o caminho direto
 * upload -> Mongo para nao atrasar o envio de uma foto de avatar, aqui o
 * arquivo passa por {@link StagingService}: e um documento de negocio e perder
 * um XML de nota ou um contrato por uma falha de rede nao e aceitavel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenericoDocumentoService {

    /** Tipos aceitos. Documento nao e imagem, entao o catalogo e mais amplo. */
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/pdf",
            "application/xml", "text/xml",
            "application/json",
            "text/csv", "text/plain",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip", "application/octet-stream"
    );

    private static final long TAMANHO_MAXIMO = 50L * 1024 * 1024; // 50MB, igual ao multipart

    private final DocumentoMongoRepository documentoRepository;
    private final StagingService staging;

    /**
     * Fluxo completo: staging -> Mongo -> limpeza do temporario.
     *
     * @param modulo modulos que podem acessar este documento. Vazio ou nulo
     *               significa "somente quem tem acesso total" (ADMIN/SUPERUSER).
     *
     * Se o Mongo falhar, o arquivo permanece em staging e a excecao menciona o
     * caminho, para que nada se perca e o reenvio possa ser feito depois.
     */
    public DocumentoArquivo salvar(
            Long empresaId, String modulo, String tipoEntidade, Long entidadeId,
            MultipartFile arquivo) throws IOException {

        String contentType = arquivo.getContentType();
        if (contentType == null || !TIPOS_PERMITIDOS.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Tipo de documento invalido: " + contentType + ". Permitidos: " + TIPOS_PERMITIDOS);
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Documento excede o limite de 50MB");
        }

        Path temporario = staging.gravar(arquivo, tipoEntidade);
        try {
            DocumentoArquivo salvo = gravarNoMongo(
                    empresaId, tipoEntidade, entidadeId, modulo,
                    arquivo.getOriginalFilename(), contentType,
                    staging.ler(temporario), staging.tamanho(temporario));

            staging.confirmar(temporario);
            return salvo;

        } catch (RuntimeException e) {
            log.error("Falha ao gravar no Mongo o documento '{}'. O arquivo ficou em {} para recuperacao. Causa: {}",
                    arquivo.getOriginalFilename(), temporario, e);
            throw new DocumentoNaoPersistidoException(
                    "Nao foi possivel comunicar com o banco de documentos. "
                            + "O arquivo foi preservado em: " + temporario, temporario, e);
        }
    }

    /** Sobrecarga sem modulo, para quem chama de dentro do ERP. */
    public DocumentoArquivo salvar(
            Long empresaId, String tipoEntidade, Long entidadeId, MultipartFile arquivo) throws IOException {
        return salvar(empresaId, null, tipoEntidade, entidadeId, arquivo);
    }

    /** Grava bytes ja em memoria (usado pela migracao em lote). */
    public DocumentoArquivo salvarBytes(
            Long empresaId, String tipoEntidade, Long entidadeId,
            String nomeArquivo, String contentType, byte[] conteudo) {
        return salvarBytes(empresaId, null, tipoEntidade, entidadeId,
                nomeArquivo, contentType, conteudo);
    }

    public DocumentoArquivo salvarBytes(
            Long empresaId, String modulo, String tipoEntidade, Long entidadeId,
            String nomeArquivo, String contentType, byte[] conteudo) {

        return gravarNoMongo(empresaId, tipoEntidade, entidadeId, modulo,
                nomeArquivo, contentType, conteudo, conteudo.length);
    }

    private DocumentoArquivo gravarNoMongo(
            Long empresaId, String tipoEntidade, Long entidadeId, String modulo,
            String nomeArquivo, String contentType, byte[] conteudo, long tamanho) {

        if (empresaId != null && entidadeId != null) {
            documentoRepository.deleteByEmpresaIdAndTipoEntidadeAndEntidadeId(
                    empresaId, tipoEntidade, entidadeId);
        }

        DocumentoArquivo doc = new DocumentoArquivo();
        doc.setEmpresaId(empresaId);
        doc.setModulo(modulo);
        doc.setTipoEntidade(tipoEntidade);
        doc.setEntidadeId(entidadeId);
        doc.setNomeArquivo(nomeArquivo);
        doc.setContentType(contentType);
        doc.setTamanho(tamanho);
        doc.setHash(sha256(conteudo));
        doc.setConteudo(conteudo);
        doc.setCriadoEm(LocalDateTime.now());
        doc.setAtualizadoEm(LocalDateTime.now());

        return documentoRepository.save(doc);
    }

    /**
     * Documentos visiveis para quem tem esses modulos liberados.
     * Modulo sem liberacao nao entra no resultado.
     */
    public List<DocumentoArquivo> listarPorModulos(Long empresaId, java.util.Set<String> modulosLiberados) {
        if (modulosLiberados == null || modulosLiberados.isEmpty()) {
            return List.of();
        }
        return documentoRepository.findByEmpresaIdAndModuloIn(empresaId, List.copyOf(modulosLiberados));
    }

    public DocumentoArquivo buscar(Long empresaId, String tipoEntidade, Long entidadeId) {
        return documentoRepository
                .findByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, tipoEntidade, entidadeId)
                .orElse(null);
    }

    public DocumentoArquivo buscarPorId(String id) {
        return documentoRepository.findById(id).orElse(null);
    }

    public byte[] lerConteudo(String id) {
        return documentoRepository.findById(id).map(DocumentoArquivo::getConteudo).orElse(null);
    }

    public List<DocumentoArquivo> listar(Long empresaId, String tipoEntidade) {
        return documentoRepository.findByEmpresaIdAndTipoEntidade(empresaId, tipoEntidade);
    }

    public void remover(Long empresaId, String tipoEntidade, Long entidadeId) {
        documentoRepository.deleteByEmpresaIdAndTipoEntidadeAndEntidadeId(
                empresaId, tipoEntidade, entidadeId);
    }

    static String sha256(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
