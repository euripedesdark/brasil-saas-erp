package br.com.brasil_saas.bi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Conexao analitica opcional. Nao substitui o DataSource principal do ERP.
 * As credenciais sao fornecidas exclusivamente por variaveis de ambiente.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "bi.datasource", name = "enabled", havingValue = "true")
public class BiDataSourceConfig {

    @Bean(name = "biDataSource", destroyMethod = "close")
    public HikariDataSource biDataSource(org.springframework.core.env.Environment environment) {
        String url = required(environment, "BI_DB_URL");
        String username = required(environment, "BI_DB_USER");
        String password = required(environment, "BI_DB_PASSWORD");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("brasil-saas-bi-reader");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setConnectionInitSql("SET default_transaction_read_only = on");
        return new HikariDataSource(config);
    }

    @Bean("biNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate biNamedParameterJdbcTemplate(
            @Qualifier("biDataSource") DataSource dataSource) {
        org.springframework.jdbc.core.JdbcTemplate template = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        template.setQueryTimeout(5);
        return new NamedParameterJdbcTemplate(template);
    }

    private static String required(org.springframework.core.env.Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel obrigatoria para BI ausente: " + name);
        }
        return value;
    }
}
