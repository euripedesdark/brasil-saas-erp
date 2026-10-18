package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.config.GrafanaBiProperties;
import br.com.brasil_saas.bi.service.BiEscopoService;
import br.com.brasil_saas.shared.dto.ApiResponse;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.net.URI;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onde o dashboard do BI esta, e o que a pessoa pode ver nele.
 *
 * <p>Nao e proxy: o Grafana fala direto com o navegador e com o banco. Aqui
 * so sai o endereco e o escopo, para a tela de BI montar a URL. O Grafana fica
 * responsavel por autenticar (mesma conta do AD) e porfirar os dados.
 */
@RestController
@RequestMapping("/api/bi/grafana")
public class BiGrafanaController {

    private final GrafanaBiProperties properties;
    private final BiEscopoService escopo;

    public BiGrafanaController(GrafanaBiProperties properties, BiEscopoService escopo) {
        this.properties = properties;
        this.escopo = escopo;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<Dashboard> dashboard(@AuthenticationPrincipal AuthenticatedUser usuario) {
        String url = enderecoPublico(properties.getDashboardUrl());
        List<String> areas = escopo.areasVisiveis(usuario);
        return ApiResponse.success(new Dashboard(url, areas, escopo.veTodasAsAreas(usuario)));
    }

    /** So aceita http/https sem credencial, query ou fragmento. */
    private String enderecoPublico(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            URI uri = URI.create(valor);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return null;
            }
            return valor;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param url        Endereco do dashboard.
     * @param areas      Areas liberadas para a pessoa (o filtro do painel).
     * @param completo   Verdadeiro quando a pessoa ve todas as areas.
     */
    public record Dashboard(String url, List<String> areas, boolean completo) {}
}
