package br.com.brasil_saas.ia.controller;

import br.com.brasil_saas.ia.dto.ChatSessaoRequest;
import br.com.brasil_saas.ia.model.ChatSessao;
import br.com.brasil_saas.ia.service.ChatSessaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ia/sessoes")
@Tag(name = "Chat Sessoes", description = "API para gerenciamento de sessoes de chat")
@RequiredArgsConstructor
public class ChatSessaoController {

    private final ChatSessaoService chatSessaoService;

    @PostMapping
    @Operation(summary = "Criar nova sessao de chat")
    @PreAuthorize("hasAuthority('IA_WRITE')")
    public ResponseEntity<ChatSessao> criar(@RequestHeader("X-Empresa-Id") Long empresaId,
                                            @RequestBody ChatSessaoRequest request) {
        ChatSessao sessao = chatSessaoService.criar(empresaId, request);
        return ResponseEntity.ok(sessao);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar sessao de chat")
    @PreAuthorize("hasAuthority('IA_WRITE')")
    public ResponseEntity<ChatSessao> atualizar(@RequestHeader("X-Empresa-Id") Long empresaId,
                                               @PathVariable Long id,
                                               @RequestBody ChatSessaoRequest request) {
        ChatSessao sessao = chatSessaoService.atualizar(empresaId, id, request);
        return ResponseEntity.ok(sessao);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar sessao por ID")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<ChatSessao> buscarPorId(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                @PathVariable Long id) {
        ChatSessao sessao = chatSessaoService.buscarPorId(empresaId, id);
        return ResponseEntity.ok(sessao);
    }

    @GetMapping
    @Operation(summary = "Listar todas as sessoes da empresa")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<List<ChatSessao>> listarPorEmpresa(@RequestHeader("X-Empresa-Id") Long empresaId) {
        List<ChatSessao> sessoes = chatSessaoService.listarPorEmpresa(empresaId);
        return ResponseEntity.ok(sessoes);
    }

    @GetMapping("/usuario/{usuarioId}")
    @Operation(summary = "Listar sessoes por usuario")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<List<ChatSessao>> listarPorUsuario(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                             @PathVariable Long usuarioId) {
        List<ChatSessao> sessoes = chatSessaoService.listarPorUsuario(empresaId, usuarioId);
        return ResponseEntity.ok(sessoes);
    }

    @GetMapping("/favoritos/{usuarioId}")
    @Operation(summary = "Listar sessoes favoritas do usuario")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<List<ChatSessao>> listarFavoritos(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                            @PathVariable Long usuarioId) {
        List<ChatSessao> sessoes = chatSessaoService.listarFavoritos(empresaId, usuarioId);
        return ResponseEntity.ok(sessoes);
    }

    @GetMapping("/recentes/{usuarioId}")
    @Operation(summary = "Listar sessoes recentes do usuario")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<List<ChatSessao>> listarRecentes(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                            @PathVariable Long usuarioId) {
        List<ChatSessao> sessoes = chatSessaoService.listarRecentes(empresaId, usuarioId);
        return ResponseEntity.ok(sessoes);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir sessao de chat")
    @PreAuthorize("hasAuthority('IA_DELETE')")
    public ResponseEntity<Void> excluir(@RequestHeader("X-Empresa-Id") Long empresaId,
                                        @PathVariable Long id) {
        chatSessaoService.excluir(empresaId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/favorito")
    @Operation(summary = "Alternar favorito da sessao")
    @PreAuthorize("hasAuthority('IA_WRITE')")
    public ResponseEntity<Void> alternarFavorito(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                @PathVariable Long id) {
        chatSessaoService.alternarFavorito(empresaId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/limpar")
    @Operation(summary = "Limpar historico da sessao")
    @PreAuthorize("hasAuthority('IA_WRITE')")
    public ResponseEntity<Void> limparHistorico(@RequestHeader("X-Empresa-Id") Long empresaId,
                                               @PathVariable Long id) {
        chatSessaoService.limparHistorico(empresaId, id);
        return ResponseEntity.ok().build();
    }
}
