package br.com.brasil_saas.shared.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Area de staging (pasta temporaria) para arquivos enviados pelo sistema.
 *
 * Por que existe: o upload nasce em disco e só vira documento permanente
 * depois de chegar ao MongoDB. Sem essa etapa, uma falha de comunicação no
 * instante do envio perderia o arquivo — o usuario ja teria preenchido o
 * formulario e gotten "sucesso" de um lado e nada do outro.
 *
 * O fluxo e sempre:
 * <pre>
 *   1. gravar em staging   (barato, local, confiavel)
 *   2. enviar ao Mongo
 *   3a. ok      -> apaga o temporario
 *   3b. falha   -> mantem o temporario e registra o caminho para recuperacao
 * </pre>
 *
 * Nada aqui apaga arquivo por conta propria sem um {@code confirmar} explicito,
 * para que o arquivo sobrevivente seja sempre um resgatavel.
 */
@Slf4j
@Service
public class StagingService {

    private static final DateTimeFormatter SUFIXO_TEMPO =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path raiz;

    public StagingService(
            @Value("${brasil-saas.armazenamento.staging-dir:./staging}") String diretorio) {
        this.raiz = Paths.get(diretorio).toAbsolutePath().normalize();
        try {
            Files.createDirectories(raiz);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar a pasta de staging: " + raiz, e);
        }
        log.info("Staging de arquivos em: {}", raiz);
    }

    public Path getRaiz() {
        return raiz;
    }

    /**
     * Grava o arquivo enviado em staging e devolve o caminho.
     * Nao ha interacao com a rede aqui: se esta chamada der erro, o problema
     * e disco, nao MongoDB.
     */
    public Path gravar(MultipartFile arquivo, String prefixo) throws IOException {
        String original = arquivo.getOriginalFilename() == null
                ? "arquivo"
                : arquivo.getOriginalFilename();
        // Fixa o nome do usuario: nao confiar em caminho vindo do cliente
        String seguro = original.replaceAll("[^A-Za-z0-9._-]", "_");
        String destino = prefixoSeguro(prefixo) + "-" + LocalDateTime.now().format(SUFIXO_TEMPO)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + seguro;

        Path caminho = validarCaminho(raiz.resolve(destino));
        Files.createDirectories(raiz);
        try (InputStream in = arquivo.getInputStream()) {
            Files.copy(in, caminho, StandardCopyOption.REPLACE_EXISTING);
        }
        return caminho;
    }

    /** Versao para migracao em lote, a partir de um arquivo ja existente em disco. */
    public Path copiarPara(Path origem, String prefixo) throws IOException {
        String nome = origem.getFileName().toString();
        String destino = prefixoSeguro(prefixo) + "-" + LocalDateTime.now().format(SUFIXO_TEMPO)
                + "-" + UUID.randomUUID().toString().substring(0, 8) + "-" + nome;
        Path caminho = validarCaminho(raiz.resolve(destino));
        Files.createDirectories(raiz);
        Files.copy(origem, caminho, StandardCopyOption.REPLACE_EXISTING);
        return caminho;
    }

    /** Le de volta o conteudo staged. */
    public byte[] ler(Path caminho) throws IOException {
        return Files.readAllBytes(validarCaminho(caminho));
    }

    public long tamanho(Path caminho) throws IOException {
        return Files.size(validarCaminho(caminho));
    }

    /** Etapa 3a: deu certo, o temporario pode sumir. */
    public void confirmar(Path caminho) {
        try {
            Files.deleteIfExists(validarCaminho(caminho));
        } catch (IOException e) {
            log.warn("Nao consegui remover o temporario {}: {}", caminho, e.toString());
        }
    }

    /**
     * Temporarios que sobraram de envios que nao chegaram ao Mongo.
     * Permite reenviar em lote depois que o banco voltar.
     */
    private static String prefixoSeguro(String prefixo) {
        if (prefixo == null || !prefixo.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("Prefixo de arquivo invalido");
        }
        return prefixo;
    }

    private Path validarCaminho(Path caminho) throws IOException {
        Path normalizado = caminho.toAbsolutePath().normalize();
        if (!normalizado.startsWith(raiz) || normalizado.equals(raiz)
                || Files.isSymbolicLink(normalizado)
                || !normalizado.getParent().toRealPath().startsWith(raiz.toRealPath())) {
            throw new IllegalArgumentException("Arquivo fora da area de staging");
        }
        return normalizado;
    }

    public List<Path> pendentes() {
        if (!Files.isDirectory(raiz)) return List.of();
        try (Stream<Path> s = Files.list(raiz)) {
            return s.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            log.warn("Nao consegui listar o staging {}: {}", raiz, e.toString());
            return List.of();
        }
    }
}
