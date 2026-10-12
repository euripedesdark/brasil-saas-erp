package br.com.brasil_saas.shared.service;

import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.image.ImagemMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Importa a pasta local de imagens para a coleção "imagens" do MongoDB.
 *
 * Operação em duas fases, de propósito:
 * <ol>
 *   <li><b>importar</b> — não destrutivo, grava tudo no Mongo e devolve um manifesto</li>
 *   <li><b>purgar</b> — apaga do disco apenas o que já foi confirmado no Mongo</li>
 * </ol>
 * Fazer as duas juntas e chamando de "mover" deixa o sistema sem imagem se a
 * segunda metade falhar no meio do caminho.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImagemMigracaoService {

    /** tipos aceitos na importacao, mais largos que os do upload (inclui .bmp legado) */
    private static final Map<String, String> TIPOS_POR_EXTENSAO = Map.ofEntries(
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".bmp", "image/bmp"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".svg", "image/svg+xml")
    );

    private static final Set<String> IGNORAR = Set.of(
            ".gitkeep", ".DS_Store", "thumbs.db");

    public static final String TIPO_ARQUIVO_LOCAL = "arquivo_local";

    private final ImagemMongoRepository imagemRepository;
    private final StagingService staging;

    /**
     * Fase 1 — copia tudo para o Mongo sem tocar no disco.
     * Reexecutar e seguro: o que ja estiver la (mesmo hash) e apenas pulado.
     */
    public ResultadoImportacao importar(Path pasta, String tipoEntidade) {
        List<String> importados = new ArrayList<>();
        List<String> jaExistentes = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        long bytes = 0;

        List<Path> arquivos = listar(pasta);
        log.info("Importacao de imagens: {} arquivo(s) em {}", arquivos.size(), pasta);

        for (Path arquivo : arquivos) {
            String nome = arquivo.getFileName().toString();
            try {
                byte[] conteudo = Files.readAllBytes(arquivo);
                String hash = GenericoDocumentoService.sha256(conteudo);

                // ja esta no Mongo? nao duplica
                if (!imagemRepository.findFirstByHash(hash).isEmpty()) {
                    jaExistentes.add(nome);
                    continue;
                }

                // staging antes do Mongo: mesmo caminho de seguranca dos uploads
                Path temporario = staging.copiarPara(arquivo, tipoEntidade);
                try {
                    ImagemDocumento doc = new ImagemDocumento();
                    doc.setEmpresaId(null);
                    doc.setTipoEntidade(tipoEntidade);
                    doc.setEntidadeId(null);
                    doc.setNomeArquivo(nome);
                    doc.setContentType(contentTypeDe(nome));
                    doc.setTamanho(conteudo.length);
                    doc.setHash(hash);
                    doc.setConteudo(conteudo);
                    doc.setCriadoEm(LocalDateTime.now());
                    doc.setAtualizadoEm(LocalDateTime.now());
                    imagemRepository.save(doc);

                    staging.confirmar(temporario);
                    importados.add(nome);
                    bytes += conteudo.length;

                } catch (RuntimeException e) {
                    // mantem o temporario para nao perder o arquivo
                    log.error("Falha ao enviar {} ao Mongo; preservado em {}", nome, temporario);
                    falhas.add(nome + " -> " + temporario);
                }

            } catch (IOException e) {
                log.error("Falha de leitura em {}", arquivo, e);
                falhas.add(nome + " -> " + e.getMessage());
            }
        }

        return new ResultadoImportacao(importados, jaExistentes, falhas, arquivos.size(), bytes);
    }

    /**
     * Fase 2 — apaga do disco somente o que ja foi confirmado no Mongo.
     * Reconsulta o banco por nome antes de apagar, para nunca remover orfao.
     */
    public ResultadoPurga purgar(Path pasta, String tipoEntidade) {
        List<String> removidos = new ArrayList<>();
        List<String> preservados = new ArrayList<>();

        for (Path arquivo : listar(pasta)) {
            String nome = arquivo.getFileName().toString();
            boolean noMongo = imagemRepository
                    .findByTipoEntidade(tipoEntidade).stream()
                    .anyMatch(i -> nome.equals(i.getNomeArquivo())
                            && i.getConteudo() != null
                            && i.getConteudo().length > 0);

            if (noMongo) {
                try {
                    Files.delete(arquivo);
                    removidos.add(nome);
                } catch (IOException e) {
                    log.warn("Nao consegui apagar {}: {}", arquivo, e.toString());
                    preservados.add(nome);
                }
            } else {
                preservados.add(nome);
            }
        }
        return new ResultadoPurga(removidos, preservados);
    }

    private List<Path> listar(Path pasta) {
        if (!Files.isDirectory(pasta)) return List.of();
        try (Stream<Path> s = Files.list(pasta)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> !IGNORAR.contains(p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Nao consegui listar {}: {}", pasta, e.toString());
            return List.of();
        }
    }

    private String contentTypeDe(String nome) {
        int i = nome.lastIndexOf('.');
        String ext = i < 0 ? "" : nome.substring(i).toLowerCase(Locale.ROOT);
        return TIPOS_POR_EXTENSAO.getOrDefault(ext, "application/octet-stream");
    }

    public record ResultadoImportacao(
            List<String> importados,
            List<String> jaExistentes,
            List<String> falhas,
            int totalVisto,
            long bytes) {
    }

    public record ResultadoPurga(List<String> removidos, List<String> preservados) {
    }
}
