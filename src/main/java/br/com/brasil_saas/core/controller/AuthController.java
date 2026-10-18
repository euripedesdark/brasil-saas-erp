package br.com.brasil_saas.core.controller;

import br.com.brasil_saas.core.service.AuthService;
import br.com.brasil_saas.core.service.dto.LoginRequest;
import br.com.brasil_saas.core.service.dto.LoginResponse;
import br.com.brasil_saas.core.service.dto.UserProfileResponse;
import br.com.brasil_saas.shared.model.ApiResponse;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.security.CustomUserDetailsService;
import br.com.brasil_saas.shared.security.SpnegoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SpnegoService spnegoService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Login pelo provider POSTGRES do IAM compartilhado.
     * A validação de credenciais e certificados pertence ao Auth Service.
     */
    @PostMapping("/login/database")
    public ResponseEntity<ApiResponse<LoginResponse>> loginDatabase(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.loginBanco(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Alias explícito para o fluxo Managed (AD). Mantém /login compatível.
     */
    @PostMapping("/login/ad")
    public ResponseEntity<ApiResponse<LoginResponse>> loginAd(
            @Valid @RequestBody LoginRequest request) {
        request.setProvider("AD");
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Entrada sem senha, pelo tíquete que o navegador já tem.
     *
     * <p>Em uso normal ninguém chama isto à mão: o navegador do Windows unido ao
     * domínio faz o desafio sozinho. A rota existe para quando a interface
     * precisa do token e não tem como negociar — o proxy que fala com o ERP, ou o
     * teste de que o SPNEGO está valendo.
     *
     * <p>O token vem do header {@code Authorization: Negotiate ...} e é
     * <b>decifrado</b> contra o keytab do serviço. Confiar num header de nome de
     * usuário seria afirmar a identidade em vez de prová-la: qualquer cliente que
     * alcance a porta 8080 escreveria o nome que quisesse. Por isso o header não
     * entra no JWT como está — ele vira principal só depois de passar pela
     * validação criptográfica.
     *
     * <p>Responde 401 quando não há tíquete válido. O login por senha continua
     * disponível e não muda em nada: este é um atalho, não uma substituição.
     */
    @GetMapping("/spnego")
    public ResponseEntity<ApiResponse<LoginResponse>> spnego(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.regionMatches(true, 0, "Negotiate ", 0, 10)) {
            return ResponseEntity.status(401).body(ApiResponse.success(null));
        }

        String principal = spnegoService.principalDoTiquete(authorization.substring(10));
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.success(null));
        }

        String username = CustomUserDetailsService.nomeNoAd(principal);
        LoginResponse response = authService.loginPorPrincipal(username);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        LoginResponse response = authService.refresh(token);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal AuthenticatedUser user) {
        // /api/auth/** e permitAll (SecurityConfig), entao este metodo e chamado
        // sem principal. Sem esta guarda o user.getId() estoura e a resposta e 500,
        // em vez de 401. O front trata 401 como sessao expirada e joga para /login;
        // com 500 o AuthProvider mantem o usuario velho e a tela fica logada e
        // quebrada ao mesmo tempo. Mesmo padrao do metodo spnego(), linhas 60-62.
        if (user == null) {
            return ResponseEntity.status(401).body(ApiResponse.success(null));
        }
        UserProfileResponse response = authService.me(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
