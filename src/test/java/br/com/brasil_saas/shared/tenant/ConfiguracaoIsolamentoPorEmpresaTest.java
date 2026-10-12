package br.com.brasil_saas.shared.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;

import org.hibernate.annotations.TenantId;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * O filtro de empresa so funciona se DUAS coisas estiverem certas ao mesmo
 * tempo: a anotacao {@code @TenantId} no {@code TenantEntity}, e a propriedade
 * que registra o resolver no Hibernate.
 *
 * <p><b>Por que um teste para uma chave de configuracao.</b> Porque a segunda
 * falha e' silenciosa, e silencioso neste mecanismo significa isolamento
 * desativado. Com a chave errada o Hibernate nao reclama: sobe, valida o
 * schema, executa as queries sem clausula de empresa, e a aplicacao funciona —
 * com o diretor vendo os cadastros das empresas dos outros. Nenhum log,
 * nenhuma excecao, nenhum teste de tela quebra. Foi o que aconteceu na
 * primeira tentativa, com a chave escrita como
 * {@code multi_tenant_identifier_resolver} em vez de
 * {@code tenant_identifier_resolver}.
 *
 * <p>Comparar com {@link AvailableSettings} e' o que torna este teste uma
 * prova e nao uma verificacao: se o Hibernate mudar a chave numa versao
 * futura, o build falha em vez do filtro sumir.
 */
class ConfiguracaoIsolamentoPorEmpresaTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value, String caminho) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new AssertionError(caminho + " nao e um mapa: " + value);
        }
        return (Map<String, Object>) map;
    }

    private static Map<String, Object> hibernateDoYml() throws Exception {
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> raiz = asMap(new Yaml().load(in), "raiz");
            Map<String, Object> spring = asMap(raiz.get("spring"), "spring");
            Map<String, Object> jpa = asMap(spring.get("jpa"), "spring.jpa");
            Map<String, Object> properties = asMap(jpa.get("properties"), "spring.jpa.properties");
            return asMap(properties.get("hibernate"), "spring.jpa.properties.hibernate");
        }
    }

    /**
     * A chave sem o prefixo {@code hibernate.}, que dentro de
     * {@code spring.jpa.properties.hibernate} ja esta escrito.
     */
    private static String chaveEsperadaNoYml() {
        String chave = AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER;
        return chave.startsWith("hibernate.")
                ? chave.substring("hibernate.".length())
                : chave;
    }

    @Test
    @DisplayName("a chave do yml e' a mesma que o Hibernate le")
    void aChaveDoYmlEAAChaveDoHibernate() throws Exception {
        assertEquals("tenant_identifier_resolver", chaveEsperadaNoYml(),
                "o Hibernate mudou o nome da propriedade. Se a constante mudou, o "
                        + "application.yml precisa mudar junto — e sem isso o filtro "
                        + "para de valer sem nenhuma mensagem.");

        assertTrue(hibernateDoYml().containsKey(chaveEsperadaNoYml()),
                "o application.yml nao tem a chave " + chaveEsperadaNoYml()
                        + ". Sem ela o @TenantId fica registrado e nunca exercitado.");
    }

    @Test
    @DisplayName("o yml aponta para o resolver do projeto")
    void oYmlApontaParaOResolver() throws Exception {
        Object valor = hibernateDoYml().get("tenant_identifier_resolver");

        assertNotNull(valor, "a propriedade sumiu do yml");
        assertEquals(EmpresaTenantIdentifierResolver.class.getName(), valor,
                "o yml aponta para outra classe: o filtro passa a usar o tenant "
                        + "de outro lugar, e o isolamento passa a ser o que aquele "
                        + "resolver fizer.");
    }

    @Test
    @DisplayName("o TenantEntity tem o @TenantId, que e' o que aciona o filtro")
    void oTenantEntityTemOTenantId() throws Exception {
        // Sem a anotacao no campo, a propriedade do yml resolve um tenant e o
        // Hibernate ignora: o filtro e' configurado e nao aplicado. A anotacao e'
        // o que fecha o circuito, e por isso o teste a checa no campo, e nao
        // apenas a configuracao.
        Field campo = Class.forName("br.com.brasil_saas.shared.model.TenantEntity")
                .getDeclaredField("empresaId");

        assertNotNull(campo.getAnnotation(TenantId.class),
                "TenantEntity.empresaId perdeu o @TenantId. A propriedade do yml "
                        + "continua la e continua valendo, e o filtro nao acontece.");
    }

    @Test
    @DisplayName("o sentinela nao colide com nenhuma empresa")
    void oSentinelaNaoExisteComoEmpresa() {
        // Se o id -1 existir, o superuser passaria a ler e gravar aquela
        // empresa, e o sentinelo deixaria de significar "sem filtro". A sequencia
        // comeca em 1; o teste fixa o contrato em vez de confiar nisso.
        assertTrue(EmpresaTenantIdentifierResolver.SEM_FILTRO < 0,
                "o sentinela tem de ser um id que a sequencia nunca produza");
    }

    @Test
    @DisplayName("o ddl-auto continua validate")
    void ddlAutoContinuaValidate() throws Exception {
        // Fora do escopo desta mudanca, mas o filtro torna o assunto mais caro:
        // com `update`, uma coluna faltando seria criada calada e o
        // nullable=false do empresa_id nao seguraria. Um insert sem empresa
        // passaria pelo schema e so apareceria como dado invisivel.
        // ddl-auto fica em spring.jpa.hibernate, e as propriedades do Hibernate
        // em spring.jpa.properties.hibernate. Sao irmaos, nao o mesmo mapa — ler
        // um no lugar do outro da null, e null nao falha: so nao verifica nada.
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            Map<String, Object> raiz = asMap(new Yaml().load(in), "raiz");
            Map<String, Object> spring = asMap(raiz.get("spring"), "spring");
            Map<String, Object> jpa = asMap(spring.get("jpa"), "spring.jpa");
            Map<String, Object> hibernate = asMap(jpa.get("hibernate"), "spring.jpa.hibernate");
            assertEquals("validate", hibernate.get("ddl-auto"));
        }
    }
}
