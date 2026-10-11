package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.config.GraylogBiProperties;
import br.com.brasil_saas.shared.dto.ApiResponse;
import java.net.URI;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bi/integracao/graylog")
public class GraylogBiController {
    private final GraylogBiProperties properties;
    public GraylogBiController(GraylogBiProperties properties) { this.properties = properties; }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ApiResponse<Connection> status() {
        String web = publicUrl(properties.getWebUrl());
        boolean configured = properties.isConfigured() && web != null;
        // Registration of a connection does not mean a custom query widget is installed.
        return ApiResponse.success(new Connection(configured, false, configured ? web : null,
                configured ? properties.getVersion() : null));
    }

    private String publicUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            return value;
        } catch (IllegalArgumentException e) { return null; }
    }

    public record Connection(boolean configured, boolean presentationReady, String webUrl, String version) {}
}
