package br.com.brasil_saas.bi.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class BiDataSourceConfigTest {
    @Test void desabilitadoNaoCriaPoolNemDataSourceOperacional() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("bi.datasource.enabled", "false"));
            context.register(BiDataSourceConfig.class); context.refresh();
            assertFalse(context.containsBean("biReadClient"));
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }
    @Test void exigeIdentidadeSeparadaMesmoComSenha() {
        var environment = new MockEnvironment().withProperty("bi.datasource.url", "jdbc:postgresql://localhost/brasil_saas_bi")
                .withProperty("bi.datasource.username", "sa").withProperty("bi.datasource.password", "fixture");
        assertThrows(IllegalStateException.class, () -> new BiDataSourceConfig().biReadClient(environment));
    }
    @Test void semSenhaExigeTlsEArquivosAntesDeAbrirConexao() {
        assertThrows(IllegalStateException.class, () -> BiDataSourceConfig.validateCertificate(
                "jdbc:postgresql://localhost/brasil_saas_bi?sslmode=disable", "brasil_saas_bi_reader"));
        assertThrows(IllegalStateException.class, () -> BiDataSourceConfig.validateCertificate(
                "jdbc:postgresql://localhost/brasil_saas_bi?sslmode=verify-ca&sslcert=/inexistente", "brasil_saas_bi_reader"));
    }
}
