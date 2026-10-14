package br.com.brasil_saas.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibilidade com telas que enviam ordenação no formato legado
 * {@code ?sort=nome&direction=desc}, em vez do formato Spring Data
 * {@code ?sort=nome,desc} (ignorado silenciosamente pelo {@code Pageable},
 * o que fazia a grade exibir o indicador de ordenação sem ordenar).
 *
 * <p>Quando {@code direction} acompanha valores de {@code sort} sem direção
 * explícita, os parâmetros são fundidos antes da resolução dos argumentos.
 * Chamadas que já usam o formato {@code campo,direção} passam intactas.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LegacySortFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String[] sorts = request.getParameterValues("sort");
        String direction = request.getParameter("direction");
        if (sorts != null && sorts.length > 0 && direction != null
                && ("asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction))
                && Arrays.stream(sorts).anyMatch(s -> s != null && !s.contains(","))) {
            String dir = direction.toLowerCase(Locale.ROOT);
            Map<String, String[]> params = new LinkedHashMap<>(request.getParameterMap());
            params.put("sort", Arrays.stream(sorts)
                    .map(s -> (s != null && !s.contains(",")) ? s + "," + dir : s)
                    .toArray(String[]::new));
            params.remove("direction");
            request = new ParamOverrideRequest(request, params);
        }
        filterChain.doFilter(request, response);
    }

    private static final class ParamOverrideRequest extends HttpServletRequestWrapper {
        private final Map<String, String[]> params;

        ParamOverrideRequest(HttpServletRequest request, Map<String, String[]> params) {
            super(request);
            this.params = Collections.unmodifiableMap(params);
        }

        @Override
        public String getParameter(String name) {
            String[] values = params.get(name);
            return (values == null || values.length == 0) ? null : values[0];
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return params;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(params.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return params.get(name);
        }
    }
}
