package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.service.BiEscopoService;
import br.com.brasil_saas.shared.dto.ApiResponse;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * O dashboard do BI, dentro da tela do sistema.
 *
 * <p><b>O Grafana nao aparece na rede.</b> Ele escuta apenas no loopback, e
 * tudo que chega nele passa por este controller, que exige sessao do ERP. O
 * iframe da tela de BI aponta para {@code /api/bi/grafana/...}; o Grafana,
 * com {@code root_url} nesse prefixo, gera os assets e as chamadas de API no
 * mesmo prefixo, e aqui cada uma e repassada para o loopback.
 *
 * <p><b>A identidade e do ERP.</b> O cabecalho {@code X-WEBAUTH-USER} e
 * escrito aqui, nunca pelo navegador, com o usuario da sessao. O Grafana le
 * esse cabecalho e abre a sessao da pessoa. Como o Grafana esta em
 * {@code auth.proxy}, o login do Grafana segue sendo o do AD (LDAP) para
 * quem entra direto nele.
 *
 * <p><b>O filtro e do servidor.</b> As areas que a pessoa pode ver saem de
 * {@link BiEscopoService}, fechadas, e viajam em {@code var-area}. Nenhuma
 * consulta do painel aceita outro valor.
 */
@RestController
@RequestMapping("/api/bi/grafana")
public class BiGrafanaController {

    /** Cabecalho que o Grafana le para saber com quem esta. */
    private static final String HEADER_USUARIO = "X-WEBAUTH-USER";

    private static final String[] TIPOS_ACEITOS = {
            "application/javascript", "text/javascript", "text/css", "application/json",
            "image/png", "image/svg+xml", "image/gif", "image/webp", "font/woff",
            "font/woff2", "application/font-woff", "text/html", "text/plain",
            "application/octet-stream", "application/manifest+json" };

    private final BiEscopoService escopo;
    private final RestClient grafana;
    private final String dashboardUid;

    public BiGrafanaController(BiEscopoService escopo,
                               RestClient.Builder builder,
                               @Value("${bi.grafana.url:http://127.0.0.1:3000}") String url,
                               @Value("${bi.grafana.dashboard-uid:bi-brasil-saas}") String dashboardUid) {
        this.escopo = escopo;
        this.grafana = builder.baseUrl(url).build();
        this.dashboardUid = dashboardUid;
    }

    /**
     * Endereco do dashboard para o iframe montar.
     *
     * <p>A URL e relativa de proposito: o iframe fica na mesma origem do
     * sistema e leva o cookie de sessao.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<Dashboard> dashboard(@AuthenticationPrincipal AuthenticatedUser usuario) {
        List<String> areas = escopo.areasVisiveis(usuario);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("orgId", 1);
        query.put("kiosk", "tvpc");
        query.put("refresh", "5m");
        query.put("theme", "dark");
        query.put("paneload", "full");
        // A empresa vem do token: e' o isolamento multi-empresa, e nunca da URL.
        query.put("var-empresa", String.valueOf(usuario.getEmpresaId()));
        // A area vem fechada do escopo, calculado no servidor a partir dos
        // grupos do AD. Vem separada por virgula, que e o que o painel le.
        if (!areas.isEmpty()) {
            query.put("var-area", String.join(",", areas));
        }
        return ApiResponse.success(new Dashboard(
                "/api/bi/grafana/d/" + dashboardUid,
                query,
                areas,
                escopo.veTodasAsAreas(usuario)));
    }

    /**
     * Repassa a chamada ao Grafana, ja autenticada como a pessoa da sessao.
     */
    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser usuario) throws IOException {
        String caminho = request.getRequestURI().substring("/api/bi/grafana".length());
        if (caminho.isEmpty() || caminho.equals("/")) {
            // A raiz do Grafana e a propria tela de login dele; dentro do
            // sistema o que interessa e o dashboard, servido por /dashboard.
            caminho = "/d/" + dashboardUid;
        }
        StringBuilder alvo = new StringBuilder(caminho);
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            alvo.append('?').append(query);
        }

        byte[] corpo;
        HttpHeaders respostaHeaders;
        try {
            var resposta = (HttpMethod.GET.equals(request.getMethod())
                    || "HEAD".equals(request.getMethod())
                    ? grafana.get()
                    : grafana.post())
                    .uri(alvo.toString())
                    .header(HEADER_USUARIO, usuario.getUsername())
                    .headers(h -> {
                        h.setAccept(List.of(MediaType.ALL));
                        if (request.getContentType() != null) {
                            h.setContentType(MediaType.parseMediaType(request.getContentType()));
                        }
                    })
                    .retrieve()
                    .toEntity(byte[].class);
            corpo = resposta.getBody();
            respostaHeaders = resposta.getHeaders();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Grafana indisponivel".getBytes(StandardCharsets.UTF_8));
        }

        ResponseEntity.BodyBuilder saida = ResponseEntity.ok();
        MediaType tipo = respostaHeaders.getContentType();
        saida.contentType(tipo != null ? tipo : MediaType.APPLICATION_OCTET_STREAM);
        for (String nome : new String[] { "Cache-Control", "Content-Encoding", "Etag", "Last-Modified" }) {
            String valor = respostaHeaders.getFirst(nome);
            if (valor != null) {
                saida.header(nome, valor);
            }
        }
        return saida.body(corpo);
    }

    public record Dashboard(String url, Map<String, Object> query, List<String> areas, boolean completo) {}
}
