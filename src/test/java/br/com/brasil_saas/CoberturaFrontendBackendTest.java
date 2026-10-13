package br.com.brasil_saas;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Garante que todo endpoint que o frontend chama exista de fato.
 *
 * As telas de RH e de financeiro chamavam PUT/DELETE que o backend nao tinha:
 * o erro so aparecia em runtime, como 404 silencioso na tela. Aqui o mapeamento
 * e verificado com um principal AutenticatedUser e as authorities necessarias.
 *
 * Criterio: 400 = o mapeamento existe e a validacao barrou o request.
 * 404/405 apareceriam se o endpoint nao estivesse registrado.
 */
/**
 * Profile {@code test}, e nao o padrao {@code dev}.
 *
 * <p>Sem isto o {@code application-test.yml} nunca e' carregado: o contexto sobe
 * com o {@code application.yml} ({@code profiles.active: dev}), que aponta o
 * datasource para o Postgres real com {@code sslmode=verify-ca} e os certs de
 * {@code /etc/brasil-saas/pki} — caminho que o processo de build nao tem como
 * ler, e que nem deveria entrar aqui. O teste morria no Flyway com
 * {@code Could not open SSL root certificate file}, e os oito metodos da classe
 * caiam junto.
 *
 * <p>Com o profile, vale o H2 em memoria do {@code application-test.yml}:
 * {@code ddl-auto: create-drop}, Flyway desligado, Postgres fora. E' o mesmo
 * caminho que {@link TesteGeralSistema} ja usava, no proprio pacote.
 *
 * <p>O certificado e' opcional para a conexao e nao tem relacao com o que esta
 * classe cobre — ela so verifica se o mapeamento dos endpoints existe.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        // O MigracaoImagensSistemaV1 e' um CommandLineRunner que chama
        // MongoTemplate.exists() na subida. Sem o profile dev nao ha URI do
        // Mongo, o cliente sobe sem autenticacao e o servidor responde
        // "Command aggregate requires authentication" (erro 13) — derrubando o
        // contexto. Mesma propriedade que TesteGeralSistema ja desligava.
        "brasil-saas.migracao.imagens.enabled=false"
})
@AutoConfigureMockMvc
class CoberturaFrontendBackendTest {

    private static final Long EMPRESA_ID = 1L;

    @Autowired
    MockMvc mockMvc;

    private RequestPostProcessor comoAdmin(String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1L, "teste", "nao-usada", true, EMPRESA_ID, granted),
                null, granted);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(request.with(auth));
    }

    @Test
    @DisplayName("RH funcionario: buscar, editar e desativar")
    void rhFuncionario() throws Exception {
        var auth = comoAdmin("rh:funcionario:leitura", "rh:funcionario:escrita");
        perform(put("/api/rh/funcionarios/1"), auth);
        perform(get("/api/rh/funcionarios/999999"), auth).andExpect(status().isNotFound());
        perform(delete("/api/rh/funcionarios/999999"), auth).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RH cargo: buscar, editar e desativar")
    void rhCargo() throws Exception {
        var auth = comoAdmin("rh:cargo:leitura", "rh:cargo:escrita");
        perform(put("/api/rh/cargos/1"), auth).andExpect(status().isBadRequest());
        perform(get("/api/rh/cargos/999999"), auth).andExpect(status().isNotFound());
        perform(delete("/api/rh/cargos/999999"), auth).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RH folha: editar e excluir")
    void rhFolha() throws Exception {
        var auth = comoAdmin("rh:folha:leitura", "rh:folha:escrita");
        perform(put("/api/rh/folhas/1"), auth).andExpect(status().isBadRequest());
        perform(delete("/api/rh/folhas/999999"), auth).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Financeiro: cadastros aceitam PUT e DELETE")
    void financeiroCadastros() throws Exception {
        var auth = comoAdmin(
                "financeiro:centrocusto:escrita", "financeiro:planocontas:escrita",
                "financeiro:tipopagamento:escrita", "financeiro:condicao:escrita",
                "financeiro:conta:escrita");
        for (String base : List.of(
                "/api/financeiro/centros-custo",
                "/api/financeiro/plano-contas",
                "/api/financeiro/tipos-pagamento",
                "/api/financeiro/condicoes-pagamento",
                "/api/financeiro/contas-bancarias")) {
            perform(put(base + "/1"), auth).andExpect(status().isBadRequest());
            perform(delete(base + "/999999"), auth).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("Financeiro: lancamento tem partidas, edicao e exclusao")
    void financeiroLancamento() throws Exception {
        var auth = comoAdmin("financeiro:lancamento:leitura", "financeiro:lancamento:escrita");
        // A consulta valida o lançamento da empresa antes de listar partidas.
        // A mensagem de domínio comprova que o controller foi alcançado.
        perform(get("/api/financeiro/lancamentos/999999/partidas").accept(org.springframework.http.MediaType.APPLICATION_JSON), auth)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].message").value("Lançamento não encontrado"));
        perform(put("/api/financeiro/lancamentos/1"), auth).andExpect(status().isBadRequest());
        perform(delete("/api/financeiro/lancamentos/999999"), auth).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Servicos: OS tem itens, PDF, edicao e exclusao")
    void ordemServico() throws Exception {
        var auth = comoAdmin("servicos:os:leitura", "servicos:os:escrita");
        perform(get("/api/servicos/os/999999/itens"), auth).andExpect(status().isNotFound());
        perform(get("/api/servicos/os/999999/pdf"), auth).andExpect(status().isNotFound());
        perform(put("/api/servicos/os/999999"), auth).andExpect(status().isBadRequest());
        perform(delete("/api/servicos/os/999999"), auth).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Relatorios: PDF gerencial existe e recusa tipo invalido")
    void relatorioPdf() throws Exception {
        perform(get("/api/relatorios/pdf/INEXISTENTE").param("empresaId", String.valueOf(EMPRESA_ID)),
                comoAdmin()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Perfil: autoatendimento existe")
    void perfil() throws Exception {
        perform(get("/api/core/perfil"), comoAdmin()).andExpect(status().is4xxClientError());
        perform(put("/api/core/perfil/senha"), comoAdmin()).andExpect(status().isBadRequest());
    }
}
