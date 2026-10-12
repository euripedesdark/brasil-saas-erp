package br.com.brasil_saas.fiscal.controller;

import br.com.brasil_saas.fiscal.service.CertificadoDigitalService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.service.DocumentoNaoPersistidoException;
import br.com.brasil_saas.shared.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Certificado digital para emissao de nota.
 *
 * A tela pede o caminho do .pfx a cada uso e pergunta se quer guardar. O
 * endpoint devolve os bytes para a estacao assinar; gravar no Mongo e opcional
 * e explicito.
 */
@Slf4j
@RestController
@RequestMapping("/api/fiscal/certificados")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERUSER', 'ADMIN')")
public class CertificadoDigitalController {

    private final CertificadoDigitalService certificadoService;

    /**
     * Le o certificado do caminho informado e grava/devolve conforme o body.
     */
    @PostMapping("/carregar")
    public ResponseEntity<?> carregar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CarregarRequest request) {

        try {
            CertificadoDigitalService.Resultado r = certificadoService.carregarEGuardar(
                    user.getEmpresaId(), request.caminho(), request.senha(),
                    Boolean.TRUE.equals(request.salvar()));

            Map<String, Object> resposta = new java.util.LinkedHashMap<>();
            resposta.put("bytes", r.conteudo().length);
            resposta.put("arquivo", java.nio.file.Paths.get(request.caminho()).getFileName().toString());
            resposta.put("gravado", r.gravado());
            resposta.put("documentoId", r.documento() == null ? "" : r.documento().getId());
            resposta.put("modulo", CertificadoDigitalService.MODULO);
            resposta.put("mensagem", r.gravado()
                    ? "Certificado guardado no banco de documentos (modulo fiscal)."
                    : "Certificado carregado. Nada foi gravado no banco.");

            return ResponseEntity.ok(ApiResponse.success(resposta));

        } catch (DocumentoNaoPersistidoException e) {
            return ResponseEntity.status(503).body(ApiResponse.error(
                    "ARMAZENAMENTO_INDISPONIVEL", e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("CERTIFICADO_INVALIDO", e.getMessage()));

        } catch (IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("FALHA_LEITURA", e.getMessage()));
        }
    }

    /** Certificados ja guardados no banco deste tenant. */
    @GetMapping
    public ResponseEntity<?> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        List<Map<String, Object>> lista = certificadoService.certificadosSalvos(user.getEmpresaId()).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "arquivo", d.getNomeArquivo() == null ? "" : d.getNomeArquivo(),
                        "tamanho", d.getTamanho(),
                        "criadoEm", d.getCriadoEm() == null ? "" : d.getCriadoEm().toString()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(lista));
    }

    public record CarregarRequest(String caminho, String senha, Boolean salvar) {
    }
}
