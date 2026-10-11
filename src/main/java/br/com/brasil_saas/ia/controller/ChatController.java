package br.com.brasil_saas.ia.controller;

import br.com.brasil_saas.ia.dto.ChatRequest;
import br.com.brasil_saas.ia.dto.ChatResponse;
import br.com.brasil_saas.ia.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ia/chat")
@Tag(name = "Chat IA", description = "API principal para chat com IA")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Enviar mensagem para chat")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<ChatResponse> chat(@RequestHeader("X-Empresa-Id") Long empresaId,
                                              @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(empresaId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/simples")
    @Operation(summary = "Chat simples com usuario e mensagem")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<ChatResponse> chatSimples(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                    @RequestParam Long usuarioId,
                                                    @RequestParam String mensagem) {
        ChatResponse response = chatService.chatSimples(empresaId, usuarioId, mensagem);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/contexto")
    @Operation(summary = "Chat com contexto")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<ChatResponse> chatComContexto(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                        @RequestParam Long usuarioId,
                                                        @RequestParam String mensagem,
                                                        @RequestBody List<String> contexto) {
        ChatResponse response = chatService.chatComContexto(empresaId, usuarioId, mensagem, contexto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/gerar-resposta")
    @Operation(summary = "Gerar resposta para prompt")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> gerarResposta(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                @RequestParam String prompt,
                                                @RequestParam(required = false, defaultValue = "gpt-4") String modelo,
                                                @RequestParam(required = false, defaultValue = "0.7") Double temperatura,
                                                @RequestParam(required = false, defaultValue = "4096") Integer maxTokens) {
        String resposta = chatService.gerarResposta(empresaId, prompt, modelo, temperatura, maxTokens);
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/gerar-resposta-historico")
    @Operation(summary = "Gerar resposta com historico")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> gerarRespostaComHistorico(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                            @RequestBody List<String> historico,
                                                            @RequestParam String mensagem,
                                                            @RequestParam(required = false, defaultValue = "gpt-4") String modelo,
                                                            @RequestParam(required = false, defaultValue = "0.7") Double temperatura,
                                                            @RequestParam(required = false, defaultValue = "4096") Integer maxTokens) {
        String resposta = chatService.gerarRespostaComHistorico(empresaId, historico, mensagem, modelo, temperatura, maxTokens);
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/resumir")
    @Operation(summary = "Resumir texto")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> resumirTexto(@RequestHeader("X-Empresa-Id") Long empresaId,
                                              @RequestParam String texto,
                                              @RequestParam(required = false, defaultValue = "500") Integer maxTokens) {
        String resumo = chatService.resumirTexto(empresaId, texto, maxTokens);
        return ResponseEntity.ok(resumo);
    }

    @PostMapping("/traduzir")
    @Operation(summary = "Traduzir texto")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> traduzirTexto(@RequestHeader("X-Empresa-Id") Long empresaId,
                                              @RequestParam String texto,
                                              @RequestParam String idiomaDestino) {
        String traducao = chatService.traduzirTexto(empresaId, texto, idiomaDestino);
        return ResponseEntity.ok(traducao);
    }

    @PostMapping("/corrigir")
    @Operation(summary = "Corrigir texto")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> corrigirTexto(@RequestHeader("X-Empresa-Id") Long empresaId,
                                              @RequestParam String texto) {
        String textoCorrigido = chatService.corrigirTexto(empresaId, texto);
        return ResponseEntity.ok(textoCorrigido);
    }

    @PostMapping("/extrair-informacoes")
    @Operation(summary = "Extrair informacoes do texto")
    @PreAuthorize("hasAuthority('IA_READ')")
    public ResponseEntity<String> extrairInformacoes(@RequestHeader("X-Empresa-Id") Long empresaId,
                                                   @RequestParam String texto,
                                                   @RequestParam String template) {
        String informacoes = chatService.extrairInformacoes(empresaId, texto, template);
        return ResponseEntity.ok(informacoes);
    }
}
