package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.core.config.AuthServiceProperties;
import br.com.brasil_saas.shared.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceClientTest {
    @Test void contratoHttpEncaminhaPostgresEPreservaGrupos() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>();
        server.createContext("/api/v1/identity/authenticate", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] json = "{\"identityId\":\"pg\",\"username\":\"postgres\",\"provider\":\"POSTGRES\",\"groups\":[\"POSTGRES_SUPERUSER\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length); exchange.getResponseBody().write(json); exchange.close();
        });
        server.start();
        try {
            var properties = new AuthServiceProperties();
            properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var identity = new AuthServiceClient(RestClient.builder(), properties).authenticate("postgres", "teste", "POSTGRES");
            assertEquals("POSTGRES", new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(request.get()).path("provider").asText());
            assertEquals(java.util.List.of("POSTGRES_SUPERUSER"), identity.groups());
            var error = assertThrows(BusinessException.class,
                    () -> new AuthServiceClient(RestClient.builder(), properties).authenticate("postgres", "teste", "AD"));
            assertEquals("AUTH_SERVICE_INVALID_RESPONSE", error.getCode());
        } finally { server.stop(0); }
    }
    @Test void iamDesligadoNaoFazFallback() {
        var properties = new AuthServiceProperties(); properties.setEnabled(false);
        assertThrows(IllegalStateException.class,
                () -> new AuthServiceClient(RestClient.builder(), properties).authenticate("usuario", "teste", "POSTGRES"));
    }
}
