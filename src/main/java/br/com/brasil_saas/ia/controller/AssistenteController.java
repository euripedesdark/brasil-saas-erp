package br.com.brasil_saas.ia.controller;

import br.com.brasil_saas.ia.assistente.AssistenteAcaoService;
import br.com.brasil_saas.ia.assistente.AssistenteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Assistente ERP — o especialista que responde com o ERP, e não com o modelo.
 *
 * <p>Suporta dois modos operacionais:
 * <ul>
 *   <li><b>Dúvida:</b> Consulta o catálogo do ERP, descobre a tela, o caminho do menu e o passo a passo.</li>
 *   <li><b>Ação:</b> Interpreta comandos operacionais (ex: emitir nota fiscal do pedido X, distinguindo produto/serviço).</li>
 * </ul>
 */
@Tag(name = "IA - Assistente ERP")
@RestController
@RequestMapping("/api/ia/assistente")
@RequiredArgsConstructor
public class AssistenteController {

    private final AssistenteService service;
    private final AssistenteAcaoService acaoService;

    @Operation(summary = "Pergunta ao assistente do ERP")
    @PostMapping
    @PreAuthorize("hasAuthority(IA_READ)")
    public AssistenteService.Resposta perguntar(
            @RequestHeader("X-Empresa-Id") final Long empresaId,
            final Authentication auth,
            @RequestBody final PerguntaRequest req) {
        return service.perguntar(req.pergunta(), empresaId, usuario(auth));
    }

    @Operation(summary = "Executa um comando de dúvida ou ação no assistente do ERP")
    @PostMapping("/comando")
    @PreAuthorize("hasAuthority(IA_READ)")
    public ResponseEntity<AssistenteAcaoService.ComandoResponse> processarComando(
            @RequestHeader("X-Empresa-Id") final Long empresaId,
            final Authentication auth,
            @Valid @RequestBody final ComandoRequest req) {
        return ResponseEntity.ok(acaoService.processar(
                req.texto(), req.modo(), empresaId, usuario(auth)));
    }

    @Operation(summary = "Histórico do assistente com a auditoria de cada resposta")
    @GetMapping("/auditoria")
    @PreAuthorize("hasAuthority(IA_READ)")
    public List<Object> auditoria(@RequestHeader("X-Empresa-Id") final Long empresaId) {
        return service.auditoria(empresaId);
    }

    /**
     * O nome de quem perguntou.
     */
    private String usuario(final Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "desconhecido";
        }
        return auth.getName();
    }

    /** A pergunta direta para o endpoint clássico. */
    public record PerguntaRequest(
            @NotBlank(message = "A pergunta não pode ser vazia")
            @Size(max = 500, message = "A pergunta pode ter no máximo 500 caracteres")
            String pergunta) {
    }

    /** Requisição para o motor de Dúvida / Ação. */
    public record ComandoRequest(
            @NotBlank(message = "O texto não pode ser vazio")
            @Size(max = 1000, message = "O texto pode ter no máximo 1000 caracteres")
            String texto,
            String modo) {
    }
}
