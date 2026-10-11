package br.com.brasil_saas.shared.web;

import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.service.GenericoImagemService;
import br.com.brasil_saas.shared.service.ImagemMigracaoService;
import br.com.brasil_saas.shared.service.StagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operacoes de administracao sobre o armazenamento de imagens/documentos.
 *
 * A migracao da pasta local para o Mongo e deliberadamente separada em
 * importar (nao destrutivo) e purgar (destrutivo): e o que garante que uma
 * imagem nunca suma do disco sem estar confirmada no banco.
 */
@Slf4j
@RestController
@RequestMapping("/api/superadmin/armazenamento")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
public class ArmazenamentoAdminController {

    private final ImagemMigracaoService migracaoService;
    private final GenericoImagemService imagemService;
    private final StagingService staging;

    @Value("${brasil-saas.armazenamento.pasta-imagens:./src/main/resources/static/images}")
    private String pastaImagens;

    /** Fase 1: importa a pasta local para a coleção "imagens". Nao apaga nada. */
    @PostMapping("/importar-imagens")
    public Map<String, Object> importarImagens(
            @RequestParam(value = "pasta", required = false) String pasta,
            @RequestParam(value = "tipoEntidade", defaultValue = ImagemMigracaoService.TIPO_ARQUIVO_LOCAL) String tipoEntidade) {

        Path origem = (pasta == null || pasta.isBlank())
                ? Paths.get(pastaImagens)
                : Paths.get(pasta);

        ImagemMigracaoService.ResultadoImportacao r = migracaoService.importar(origem, tipoEntidade);

        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("pasta", origem.toAbsolutePath().toString());
        saida.put("totalVisto", r.totalVisto());
        saida.put("importados", r.importados().size());
        saida.put("jaExistentes", r.jaExistentes().size());
        saida.put("falhas", r.falhas());
        saida.put("bytesImportados", r.bytes());
        saida.put("purgar", "chame POST /purgar-imagens para remover do disco os confirmados");
        return saida;
    }

    /**
     * Fase 2: apaga do disco somente o que ja consta no Mongo.
     * Reconsulta o banco por arquivo, entao nada e removido "por acaso".
     */
    @PostMapping("/purgar-imagens")
    public Map<String, Object> purgarImagens(
            @RequestParam(value = "pasta", required = false) String pasta,
            @RequestParam(value = "tipoEntidade", defaultValue = ImagemMigracaoService.TIPO_ARQUIVO_LOCAL) String tipoEntidade) {

        Path origem = (pasta == null || pasta.isBlank())
                ? Paths.get(pastaImagens)
                : Paths.get(pasta);

        ImagemMigracaoService.ResultadoPurga r = migracaoService.purgar(origem, tipoEntidade);

        Map<String, Object> saida = new LinkedHashMap<>();
        saida.put("pasta", origem.toAbsolutePath().toString());
        saida.put("removidos", r.removidos().size());
        saida.put("preservados", r.preservados());
        return saida;
    }

    /**
     * Atualiza um recurso visual do sistema direto de um arquivo do disco,
     * sem passar por upload manual. Usado para trocar a tela inicial e a logo
     * desatualizadas que ficaram no Mongo.
     */
    @PostMapping("/recurso/{tipo}")
    public Map<String, Object> atualizarRecurso(
            @PathVariable String tipo,
            @RequestParam("arquivo") String arquivo,
            @RequestParam(value = "empresaId", required = false) Long empresaId) throws IOException {

        Path caminho = Paths.get(arquivo);
        if (!Files.isRegularFile(caminho)) {
            return Map.of("erro", "arquivo nao encontrado: " + caminho.toAbsolutePath());
        }

        String nome = caminho.getFileName().toString();
        String contentType = contentTypeDe(nome);

        try (InputStream in = Files.newInputStream(caminho)) {
            ImagemDocumento salva = imagemService.salvarBytesDeArquivo(
                    empresaId, tipo, nome, contentType, in.readAllBytes());
            return Map.of(
                    "tipo", tipo,
                    "empresaId", empresaId == null ? "" : empresaId,
                    "arquivo", salva.getNomeArquivo(),
                    "contentType", salva.getContentType(),
                    "tamanho", salva.getTamanho(),
                    "id", salva.getId());
        }
    }

    /** Temporarios que ficaram sem ir ao Mongo — reenvio pendente. */
    @GetMapping("/staging-pendente")
    public Map<String, Object> stagingPendente() {
        List<Path> pendentes = staging.pendentes();
        return Map.of(
                "raiz", staging.getRaiz().toString(),
                "quantidade", pendentes.size(),
                "arquivos", pendentes.stream().map(Path::getFileName).map(Path::toString).toList());
    }

    private String contentTypeDe(String nome) {
        String n = nome.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg";
    }
}
