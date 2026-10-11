package br.com.brasil_saas.shared.identity;

import br.com.brasil_saas.core.config.AuthServiceProperties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O que este teste prova, e o que ele nao prova.
 *
 * <p><b>Prova.</b> Que o ERP continua funcionando quando o Auth Service nao
 * responde — sem excecao, sem LDAP de reserva. E que desligado
 * ({@code enabled = false}) o servico nao faz chamada nenhuma.
 *
 * <p><b>Nao prova.</b> Que o Auth Service esta no ar, nem que o contrato dele
 * bate. Para isso e preciso o servico de pe, e um teste de integracao.
 */
class IdentityServiceTest {

    private static AuthServiceProperties props(boolean enabled, String url) {
        AuthServiceProperties p = new AuthServiceProperties();
        p.setEnabled(enabled);
        p.setBaseUrl(url);
        p.setConnectTimeoutMs(200);
        p.setReadTimeoutMs(300);
        return p;
    }

    /** Porta 1 nao tem nada escutando: e o caso real de servico caido. */
    private static final String MORTO = "http://127.0.0.1:1";

    @Test
    @DisplayName("Auth Service fora do ar: indisponivel, sem excecao")
    void authServiceForaDoArNaoQuebra() {
        IdentityService svc = new IdentityService(props(true, MORTO));

        ResultadoAutenticacao r = assertDoesNotThrow(
                () -> svc.autenticar("euripedes", "senha"),
                "Auth Service fora do ar nao pode derrubar o login");

        assertTrue(r.isIndisponivel(),
                "sem servico nao se sabe nada sobre a senha: e indisponibilidade, nao recusa");
    }

    @Test
    @DisplayName("Desligado: nao chama nada, e nao recusa ninguem")
    void desligadoNaoChamaNemComSenha() {
        IdentityService svc = new IdentityService(props(false, MORTO));

        assertFalse(svc.ativo());
        assertTrue(assertDoesNotThrow(() -> svc.autenticar("euripedes", "senha")).isIndisponivel());
        assertTrue(assertDoesNotThrow(() -> svc.identidadeDe("euripedes", "senha")).isEmpty());
    }

    @Test
    @DisplayName("Sem usuario ou sem senha: recusado, e nunca indisponivel")
    void credencialIncompletaEhRecusa() {
        IdentityService svc = new IdentityService(props(true, MORTO));

        // Sem credencial nao ha o que perguntar ao servico. Isso e recusa, e nao
        // queda de infraestrutura: as duas coisas levariam o login a caminhos
        // opostos se fossem parecidas.
        assertTrue(svc.autenticar(null, "senha").isRecusado());
        assertTrue(svc.autenticar("  ", "senha").isRecusado());
        assertTrue(svc.autenticar("euripedes", null).isRecusado());
        assertTrue(svc.autenticar("euripedes", "").isRecusado());
    }

    @Test
    @DisplayName("Os grupos voltam em minusculo, que e como o ERP compara")
    void gruposVoltamEmMinusculo() {
        IdentityService svc = new IdentityService(props(false, MORTO));

        // Nomes exatamente como o AD escreve: o servico nao traduz nada.
        var dto = new IdentityDto("384320b5-ef69-4692-9903-eb9dcbd3f246", "euripedes", "AD",
                List.of("GRP_ERP_ACESSO", "GRP_ERP_ADMIN", "ERP_EMPRESA_00000000000191"));

        assertEquals(List.of("grp_erp_acesso", "grp_erp_admin", "erp_empresa_00000000000191"),
                List.copyOf(svc.grupos(dto)));
    }

    @Test
    @DisplayName("groups nulo vira lista vazia, nunca null")
    void gruposNulosNuncaViramNull() {
        IdentityService svc = new IdentityService(props(false, MORTO));

        assertTrue(new IdentityDto("id", "euripedes", "AD", null).gruposOuVazio().isEmpty());
        assertTrue(svc.grupos(null).isEmpty());
        assertTrue(svc.grupos(new IdentityDto("id", "euripedes", "AD", List.of())).isEmpty());
    }

    @Test
    @DisplayName("Grupo com espaco em volta e' aparado antes de comparar")
    void grupoComEspacoEAparado() {
        IdentityService svc = new IdentityService(props(false, MORTO));
        var dto = new IdentityDto("id", "euripedes", "AD", List.of("  GRP_ADMIN  ", "   "));

        assertEquals(List.of("grp_admin"), List.copyOf(svc.grupos(dto)));
    }

    @Test
    @DisplayName("Os tres desfechos sao distintos entre si")
    void desfechosSaoDistintos() {
        var aceito = ResultadoAutenticacao.aceito(
                new IdentityDto("id", "euripedes", "AD", List.of("GRP_ADMIN")));
        var recusado = ResultadoAutenticacao.recusado();
        var indisponivel = ResultadoAutenticacao.indisponivel();

        assertTrue(aceito.isAceito());
        assertFalse(aceito.isRecusado());
        assertFalse(aceito.isIndisponivel());

        assertTrue(recusado.isRecusado());
        assertFalse(recusado.isAceito());
        assertFalse(recusado.isIndisponivel());

        assertTrue(indisponivel.isIndisponivel());
        assertFalse(indisponivel.isAceito());
        assertFalse(indisponivel.isRecusado());
    }

    @Test
    @DisplayName("So o aceito carrega identidade; os outros nao inventam")
    void soAceitoCarregaIdentidade() {
        assertEquals("id", ResultadoAutenticacao
                .aceito(new IdentityDto("id", "euripedes", "AD", List.of())).identidade().identityId());
        assertEquals(null, ResultadoAutenticacao.recusado().identidade());
        assertEquals(null, ResultadoAutenticacao.indisponivel().identidade());
    }
}
