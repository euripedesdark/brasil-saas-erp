package br.com.brasil_saas.ia.controller;

import br.com.brasil_saas.ia.dto.AIConfigRequest;
import br.com.brasil_saas.ia.dto.AIConfigResponse;
import br.com.brasil_saas.ia.service.AIConfigService;
import br.com.brasil_saas.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ia/config")
@RequiredArgsConstructor
@Tag(name = "IA - Configuracao", description = "Configuracao da IA")
public class AIConfigController {

    private final AIConfigService aiConfigService;

    @PostMapping
    @Operation(summary = "Salvar configuracao de IA")
    public ResponseEntity<ApiResponse<AIConfigResponse>> saveConfig(
            @RequestBody AIConfigRequest request,
            @RequestParam Long empresaId) {
        AIConfigResponse response = aiConfigService.saveConfig(request, empresaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "Obter configuracao de IA")
    public ResponseEntity<ApiResponse<AIConfigResponse>> getConfig(@RequestParam Long empresaId) {
        AIConfigResponse response = aiConfigService.getConfig(empresaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/test-connection")
    @Operation(summary = "Testar conexao com API de IA")
    public ResponseEntity<ApiResponse<Boolean>> testConnection(@RequestParam Long empresaId) {
        boolean response = aiConfigService.testConnection(empresaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reset-usage")
    @Operation(summary = "Resetar uso diario de tokens")
    public ResponseEntity<ApiResponse<Void>> resetUsage(@RequestParam Long empresaId) {
        aiConfigService.resetDailyUsage(empresaId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/remaining-tokens")
    @Operation(summary = "Obter tokens restantes")
    public ResponseEntity<ApiResponse<Long>> getRemainingTokens(@RequestParam Long empresaId) {
        Long remaining = aiConfigService.getRemainingTokens(empresaId);
        return ResponseEntity.ok(ApiResponse.success(remaining));
    }
}
