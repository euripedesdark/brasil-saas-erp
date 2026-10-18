package br.com.brasil_saas.shared.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/**
 * O token de sessao tambem em cookie, para o iframe do BI carregar.
 *
 * <p><b>Por que isso existe.</b> O ERP guarda o token em {@code localStorage}
 * e o envia no cabecalho {@code Authorization}. Isso funciona para
 * {@code fetch}, mas um {@code iframe} nao envia cabecalho nenhum: ele
 * navega para a URL como um link comum. Sem cookie, o dashboard do BI
 * chegaria no controller sem sessao.
 *
 * <p><b>Por que isso nao afrouxa nada.</b> O cookie carrega o mesmo JWT, que
 * passa pela mesma validacao, tem a mesma expiracao e monta o mesmo
 * {@code SecurityContext} — ver {@link JwtAuthenticationFilter}. Ele nao e uma
 * credencial nova, e o mesmo token com outra via de entrega. Por isso:
 *
 * <ul>
 *   <li>{@code HttpOnly}: o JavaScript da pagina nao le o cookie.</li>
 *   <li>{@code SameSite=Strict}: o navegador so o manda para o proprio ERP,
 *       e nunca em navegacao vinda de fora.</li>
 *   <li>Sem {@code Secure}: o ERP tambem roda em http na rede interna, e um
 *       cookie com {@code Secure} seria descartado la, deixando o BI sem
 *       sessao justamente onde ele precisa funcionar.</li>
 * </ul>
 */
public final class TokenCookie {

    private TokenCookie() {
    }

    /** Grava o token como cookie de sessao. */
    public static ResponseCookie gravar(String token, long maxAgeSeconds) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE, token)
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Apaga o cookie de sessao. E' o que o logout usa. */
    public static ResponseCookie apagar() {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }

    /** Le o token do cookie, ou null. Usado por testes e pelo logout. */
    public static String ler(jakarta.servlet.http.HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (var cookie : cookies) {
            if (JwtAuthenticationFilter.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
