package br.com.brasil_saas.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica a requisicao pelo JWT.
 *
 * <p><b>Por que o cookie, alem do cabecalho.</b> O navegador guarda o token
 * do ERP em {@code localStorage} e o {@code apiFetch} o manda no cabecalho
 * {@code Authorization}. Um {@code iframe} nao tem como fazer isso: ele navega,
 * e nao busca. E o dashboard do BI e um iframe.
 *
 * <p>Por isso o login tambem grava o token num cookie, e este filtro aceita as
 * duas vias. O cookie nao abre porta nova: e o mesmo JWT, com a mesma
 * validacao, a mesma expiracao e o mesmo {@code SecurityContext}. Ele existe
 * so porque o iframe precisa carregar. Por isso e {@code HttpOnly} (o
 * JavaScript nao le) e {@code SameSite=Strict} (so vai para o proprio ERP).
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Nome do cookie que o login grava para o iframe do BI carregar. */
    public static final String COOKIE = "BRASIL_SAAS_TOKEN";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = tokenDoCabecalho(request);
        if (token == null) {
            token = tokenDoCookie(request);
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticar(request, token);
        }
        chain.doFilter(request, response);
    }

    private String tokenDoCabecalho(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String tokenDoCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void autenticar(HttpServletRequest request, String token) {
        if (!jwtService.validate(token) || jwtService.isRefreshToken(token)) {
            return;
        }
        try {
            UserDetails user = userDetailsService.loadUserByUsername(
                    jwtService.username(token), jwtService.adGroups(token));
            if (user.isEnabled() && user.isAccountNonLocked() && user.isAccountNonExpired()) {
                var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception e) {
            log.warn(
                    "Falha ao carregar usuário do JWT. uri='{}', username='{}'",
                    request.getRequestURI(),
                    safeUsername(token),
                    e);
        }
    }

    private String safeUsername(String token) {
        try {
            return jwtService.username(token);
        } catch (Exception e) {
            return "<token-invalido>";
        }
    }
}
