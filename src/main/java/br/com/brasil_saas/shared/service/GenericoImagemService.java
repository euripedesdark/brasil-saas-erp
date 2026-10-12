package br.com.brasil_saas.shared.service;

import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.image.ImagemMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericoImagemService {

    private final ImagemMongoRepository imagemRepository;
    private final StagingService staging;
    
    private static final List<String> TIPOS_IMAGEM_PERMITIDOS = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    
    private static final long TAMANHO_MAXIMO = 10 * 1024 * 1024; // 10MB

    @Transactional
    public ImagemDocumento salvarImagem(Long empresaId, String tipoEntidade, Long entidadeId, MultipartFile arquivo) throws IOException {
        return salvarImagemInternamente(empresaId, tipoEntidade, entidadeId, arquivo);
    }

    /**
     * Upload de imagem de sistema (fundo, tela de login, logo da empresa).
     *
     * Passa por staging como os documentos: o arquivo e gravado em disco antes
     * de ir ao Mongo, e so e apagado do staging depois que o Mongo confirma.
     * Se a comunicacao falhar no meio, a imagem antiga continua no ar e o
     * arquivo novo fica preservado para reenvio, em vez de sumir.
     */
    public ImagemDocumento salvarImagemComStaging(Long empresaId, String tipoEntidade, Long entidadeId, MultipartFile arquivo) throws IOException {
        validar(arquivo);

        Path temporario = staging.gravar(arquivo, tipoEntidade);
        try {
            byte[] conteudo = staging.ler(temporario);
            ImagemDocumento imagem = gravar(
                    empresaId, tipoEntidade, entidadeId,
                    arquivo.getOriginalFilename(), arquivo.getContentType(), conteudo);

            staging.confirmar(temporario);
            return imagem;

        } catch (RuntimeException e) {
            log.error("Falha ao gravar a imagem '{}' no Mongo. Preservada em {} para recuperacao. Causa: {}",
                    arquivo.getOriginalFilename(), temporario, e);
            throw new ImagemNaoPersistidaException(
                    "Nao foi possivel comunicar com o banco de imagens. "
                            + "O arquivo foi preservado em: " + temporario, temporario, e);
        }
    }

    @Transactional
    public ImagemDocumento salvarImagemSistema(String tipoEntidade, MultipartFile arquivo) throws IOException {
        return salvarImagemComStaging(null, tipoEntidade, null, arquivo);
    }

    /**
     * Grava um recurso do sistema a partir de bytes ja em memoria, sem upload
     * nem staging. Usado pela manutencao para trocar a tela inicial e a logo
     * desatualizadas sem depender de browser.
     *
     * Substitui a imagem anterior do mesmo tipo: o recurso de sistema e unico.
     */
    public ImagemDocumento salvarBytesDeArquivo(Long empresaId, String tipoEntidade,
                                                String nomeArquivo, String contentType, byte[] conteudo) {
        return gravar(empresaId, tipoEntidade, null, nomeArquivo, contentType, conteudo);
    }

    private void validar(MultipartFile arquivo) {
        String contentType = arquivo.getContentType();
        if (contentType == null || !TIPOS_IMAGEM_PERMITIDOS.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Tipo de arquivo inválido. Tipos permitidos: JPEG, PNG, GIF, WEBP");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Tamanho do arquivo excede o limite permitido de 10MB");
        }
    }

    private ImagemDocumento salvarImagemInternamente(Long empresaId, String tipoEntidade, Long entidadeId, MultipartFile arquivo) throws IOException {
        validar(arquivo);
        return gravar(empresaId, tipoEntidade, entidadeId,
                arquivo.getOriginalFilename(), arquivo.getContentType(), arquivo.getBytes());
    }

    private ImagemDocumento gravar(Long empresaId, String tipoEntidade, Long entidadeId,
                                   String nomeArquivo, String contentType, byte[] conteudo) {

        if (empresaId != null && entidadeId != null) {
            imagemRepository.deleteByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, tipoEntidade, entidadeId);
        } else if (empresaId == null && entidadeId == null) {
            imagemRepository.deleteByTipoEntidade(tipoEntidade);
        }

        ImagemDocumento imagem = new ImagemDocumento();
        imagem.setEmpresaId(empresaId);
        imagem.setTipoEntidade(tipoEntidade);
        imagem.setEntidadeId(entidadeId);
        imagem.setNomeArquivo(nomeArquivo);
        imagem.setContentType(contentType);
        imagem.setTamanho(conteudo.length);
        imagem.setHash(GenericoDocumentoService.sha256(conteudo));
        imagem.setConteudo(conteudo);
        imagem.setCriadoEm(LocalDateTime.now());
        imagem.setAtualizadoEm(LocalDateTime.now());

        return imagemRepository.save(imagem);
    }

    public ImagemDocumento buscarImagem(Long empresaId, String tipoEntidade, Long entidadeId) {
        return imagemRepository.findByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, tipoEntidade, entidadeId)
                .orElse(null);
    }

    /**
     * Imagens de sistema (tela de login / fundo) são decorativas: uma falha no
     * MongoDB (indisponível, credencial errada, documento legado com tipo
     * incompatível) não pode derrubar a tela de login com HTTP 500.
     * Em caso de erro, registra a causa real no log e retorna null (HTTP 204).
     */
    public ImagemDocumento buscarImagemSistema(String tipoEntidade) {
        try {
            return imagemRepository.findByTipoEntidade(tipoEntidade).stream()
                    .filter(i -> i.getConteudo() != null && i.getConteudo().length > 0)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.error("Falha ao buscar imagem de sistema '{}' no MongoDB (colecao 'imagens'): {}",
                    tipoEntidade, e);
            return null;
        }
    }

    @Transactional
    public void removerImagem(Long empresaId, String tipoEntidade, Long entidadeId) {
        imagemRepository.deleteByEmpresaIdAndTipoEntidadeAndEntidadeId(empresaId, tipoEntidade, entidadeId);
    }

    @Transactional
    public void removerImagemSistema(String tipoEntidade) {
        imagemRepository.deleteByTipoEntidade(tipoEntidade);
    }
}
