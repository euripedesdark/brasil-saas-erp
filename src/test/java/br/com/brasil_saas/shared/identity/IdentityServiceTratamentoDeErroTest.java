package br.com.brasil_saas.shared.identity;

import br.com.brasil_saas.core.config.AuthServiceProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 401 e 503 precisam chegar a quem chama como coisas diferentes.
 *
 * <p>Se os dois virassem a mesma coisa, a queda do Active Directory seria
 * tratada como senha errada — ou, pior, uma senha errada seria aceita pelo
 * caminho local. Quem opera o sistema precisa saber qual das duas aconteceu.
 *
 * <p>Estes testes sobem um servidor HTTP local que devolve cada codigo, e
 * conferem o desfecho. Nao tocam no Auth Service.
 */
class IdentityServiceTratamentoDeErroTest {

    private HttpServer servidor;

    @AfterEach
    void derrubar() {
        if (servidor != null) {
            servidor.stop(0);
            servidor = null;
        }
    }

    private String subir(int status) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/identity/authenticate", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static IdentityService ligado(String url) {
        AuthServiceProperties p = new AuthServiceProperties();
        p.setEnabled(true);
        p.setBaseUrl(url);
        p.setConnectTimeoutMs(2000);
        p.setReadTimeoutMs(3000);
        return new IdentityService(p);
    }

    @Test
    @DisplayName("401: recusa definitiva, e o login termina")
    void recusaDefinitiva() throws IOException {
        IdentityService svc = ligado(subir(401));

        ResultadoAutenticacao r = assertDoesNotThrow(() -> svc.autenticar("euripedes", "errada"));

        assertTrue(r.isRecusado(), "401 e resposta do servico, nao falta de resposta");
    }

    @Test
    @DisplayName("503: indisponibilidade, e nao diz nada sobre a senha")
    void indisponivelNaoERecusa() throws IOException {
        IdentityService svc = ligado(subir(503));

        ResultadoAutenticacao r = assertDoesNotThrow(
                () -> svc.autenticar("euripedes", "Copa@@2026"));

        assertTrue(r.isIndisponivel(),
                "503 e o servico ou o AD fora do ar; tratar como senha errada esconde a queda");
    }

    @Test
    @DisplayName("401 e 503 nunca produzem o mesmo desfecho")
    void osDoisNuncaSeConfundem() throws IOException {
        var recusa = ligado(subir(401));
        var r401 = recusa.autenticar("euripedes", "x");
        servidor.stop(0);
        servidor = null;

        var queda = ligado(subir(503));
        var r503 = queda.autenticar("euripedes", "x");

        assertTrue(r401.isRecusado());
        assertTrue(r503.isIndisponivel());
        assertTrue(!r401.equals(r503), "os desfechos precisam ser diferentes entre si");
    }

    @Test
    @DisplayName("403, 500 e 400 sao indisponibilidade: nao se sabe da senha")
    void outrosStatusNaoviramRecusa() throws IOException {
        for (int status : new int[]{403, 500, 400}) {
            servidor = null;
            IdentityService svc = ligado(subir(status));

            ResultadoAutenticacao r = assertDoesNotThrow(
                    () -> svc.autenticar("euripedes", "senha"), "status " + status);

            assertTrue(r.isIndisponivel(),
                    "status " + status + " e sobre a resposta, nao sobre a senha");
            servidor.stop(0);
            servidor = null;
        }
    }
}
