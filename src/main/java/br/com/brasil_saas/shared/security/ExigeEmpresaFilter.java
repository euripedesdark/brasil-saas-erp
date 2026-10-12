package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.shared.model.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Exige empresa para tudo que for API.
 *
 * Empresa e o tenant: e o filtro de todos os dados. Um usuario sem empresa
 * cadastrada nao deveria ver nada — nem cadastros, nem financeiro, nem
 * relatorios. Com o bloqueio so na interface, bastava chamar a API direto
 * (curl, outra tela, integracao) e os dados saiam normalmente: o guard do
 * front e convencao, nao seguranca.
 *
 * Fica num filtro unico em vez de um @PreAuthorize por controller: 81
 * controllers, e a regra e a mesma para todos.
 */
@Component
@RequiredArgsConstructor
// Depois do filtro de seguranca: e ele que coloca o usuario no
// SecurityContext. Rodando antes, o principal chegaria nulo e o filtro
// passaria toda requisição — silenciosamente sem proteger nada.
public class ExigeEmpresaFilter extends OncePerRequestFilter {

    /**
     * O que continua liberado sem empresa.
     *
     * Sem o cadastro da empresa o usuario nao consegue sair do lugar, entao
     * estas rotas precisam responder: o proprio cadastro, o login e o perfil.
     */
    private static final Set<String> LIBERADAS = Set.of(
            "/api/auth/",
            "/api/core/minha-empresa",
            "/api/core/perfil"
    );

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/") || liberada(path)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            chain.doFilter(request, response);
            return;
        }

        // SUPERUSER nao passa por esta regra. Quem administra o sistema
        // precisa entrar justamente para poder configurar empresa, permissao e
        // demais parametros — inclusive numa instalacao em que nenhuma empresa
        // foi cadastrada ainda, que e o caso tipico de quem instala.
        if (user.getEmpresaId() == null && !isSuperuser(user)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            ApiResponse<Void> corpo = ApiResponse.error(
                    java.util.List.of(new ApiResponse.ApiError(
                            null,
                            "Cadastre a empresa para usar o sistema.",
                            "EMPRESA_NAO_CADASTRADA")),
                    path, HttpServletResponse.SC_FORBIDDEN);
            objectMapper.writeValue(response.getWriter(), corpo);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isSuperuser(AuthenticatedUser user) {
        return user.getAuthorities().stream()
                .map(Object::toString)
                .map(String::toUpperCase)
                .anyMatch(a -> a.equals("ROLE_SUPERUSER") || a.equals("SUPERUSER"));
    }

    private static boolean liberada(String path) {
        for ( String p : LIBERADAS ) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }
}
