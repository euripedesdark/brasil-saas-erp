package br.com.brasil_saas.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * Entrega a SPA React pelo Spring Boot.
 *
 * APIs ficam em /api/** e seguem o pipeline normal do backend.
 * Rotas sem extensao e fora de /api/** sao tratadas como rotas do React
 * e recebem o index.html do build do Vite.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/dist/assets/");
        registry.addResourceHandler("/dist/assets/**")
                .addResourceLocations("classpath:/static/dist/assets/");
    }

    @Bean
    public OncePerRequestFilter spaForwardFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(@NonNull HttpServletRequest request,
                                            @NonNull HttpServletResponse response,
                                            @NonNull FilterChain filterChain)
                    throws ServletException, IOException {

                String uri = request.getRequestURI();

                boolean api = uri.startsWith("/api/") || uri.equals("/api");
                boolean staticAsset = uri.startsWith("/assets/")
                        || uri.startsWith("/dist/")
                        || uri.startsWith("/images/")
                        || uri.startsWith("/css/")
                        || uri.startsWith("/js/")
                        || uri.equals("/favicon.ico");
                boolean infra = uri.startsWith("/actuator")
                        || uri.startsWith("/swagger")
                        || uri.startsWith("/v3/")
                        || uri.startsWith("/error");
                boolean hasExtension = uri.contains(".") && !uri.endsWith("/");

                // O navegador solicita favicon.ico automaticamente. O ERP nao depende
                // desse arquivo; responda explicitamente sem deixar o ResourceHandler
                // gerar erro 500 por recurso inexistente.
                if (uri.equals("/favicon.ico")) {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    return;
                }

                if (!api && !staticAsset && !infra && !hasExtension) {
                    request.getRequestDispatcher("/dist/index.html").forward(request, response);
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }
}
