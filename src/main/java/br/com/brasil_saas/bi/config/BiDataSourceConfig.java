package br.com.brasil_saas.bi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;


/**
 * Conexao analitica opcional. Nao substitui o DataSource principal do ERP.
 * As credenciais sao fornecidas exclusivamente por variaveis de ambiente.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "bi.datasource", name = "enabled", havingValue = "true")
public class BiDataSourceConfig {

    @Bean(name = "biReadClient", destroyMethod = "close")
    public BiReadClient biReadClient(org.springframework.core.env.Environment environment) {
        String url = required(environment, "bi.datasource.url");
        String username = required(environment, "bi.datasource.username");
        String password = environment.getProperty("bi.datasource.password");

        if (!"brasil_saas_bi_reader".equals(username)) {
            throw new IllegalStateException("BI exige identidade exclusiva brasil_saas_bi_reader");
        }
        if (password == null || password.isBlank()) validateCertificate(url, username);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        if (password != null && !password.isBlank()) config.setPassword(password);
        config.setPoolName("brasil-saas-bi-reader");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5000);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setConnectionInitSql("SET default_transaction_read_only = on");
        var pool = new HikariDataSource(config);
        try {
            // Falha fechada se habilitado antes da PKI, grants ou views estarem prontos.
            // Nao injeta DataSource bean: autoconfiguracao operacional permanece intacta.
            validateConnection(pool);
            return new BiReadClient(pool);
        } catch (RuntimeException e) {
            pool.close();
            throw e;
        }
    }

    static void validateCertificate(String url, String username) {
        // Leitura das propriedades sem dependencia de compilacao no driver (scope runtime).
        var properties = new java.util.Properties();
        if (!url.startsWith("jdbc:postgresql://") || !url.contains("?"))
            throw new IllegalStateException("BI sem senha exige URL PostgreSQL TLS");
        for (String parameter : url.substring(url.indexOf('?') + 1).split("&")) {
            String[] pair = parameter.split("=", 2);
            if (pair.length == 2) properties.setProperty(
                    java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8));
        }
        if (properties.containsKey("password"))
            throw new IllegalStateException("Senha na URL BI nao permitida");
        if (!java.util.Set.of("verify-ca", "verify-full")
                .contains(properties.getProperty("sslmode", ""))) {
            throw new IllegalStateException("BI sem senha exige PostgreSQL TLS com CA verificada");
        }
        try {
            for (String name : java.util.List.of("sslrootcert", "sslcert", "sslkey")) {
                String value = properties.getProperty(name);
                if (value == null || !Files.isReadable(Path.of(value)) || Files.size(Path.of(value)) == 0)
                    throw new IllegalStateException("Arquivo TLS BI ausente ou ilegivel: " + name);
            }
            X509Certificate cert;
            X509Certificate ca;
            var factory = CertificateFactory.getInstance("X.509");
            try (var input = Files.newInputStream(Path.of(properties.getProperty("sslcert")))) {
                cert = (X509Certificate) factory.generateCertificate(input);
            }
            try (var input = Files.newInputStream(Path.of(properties.getProperty("sslrootcert")))) {
                ca = (X509Certificate) factory.generateCertificate(input);
            }
            cert.checkValidity();
            ca.checkValidity();
            cert.verify(ca.getPublicKey());
            var names = new LdapName(cert.getSubjectX500Principal().getName()).getRdns().stream()
                    .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType())).toList();
            if (names.size() != 1 || !username.equals(names.getFirst().getValue()))
                throw new IllegalStateException("CN do certificado BI difere da role dedicada");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Certificado BI invalido; datasource nao habilitado", e);
        }
    }

    private static void validateConnection(HikariDataSource pool) {
        try (var connection = pool.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery("SELECT current_user,current_database(),"
                    + "(SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid())")) {
                if (!result.next() || !"brasil_saas_bi_reader".equals(result.getString(1))
                        || !"brasil_saas_bi".equals(result.getString(2)) || !result.getBoolean(3))
                    throw new IllegalStateException("Identidade, banco ou TLS BI incorretos");
            }
            for (String area : java.util.List.of("financeiro", "producao", "vendas", "servicos")) {
                try (var ignored = statement.executeQuery("SELECT empresa_id FROM brasil_saas_dl.v_bi_" + area + " LIMIT 0")) {
                    // Confirma contrato minimo e grants sem varrer os dados.
                }
                try (var result = statement.executeQuery("SELECT has_table_privilege(current_user,'bi_reporting."
                        + area + "','INSERT,UPDATE,DELETE,TRUNCATE')")) {
                    if (!result.next() || result.getBoolean(1))
                        throw new IllegalStateException("Role BI reader possui escrita em " + area);
                }
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Conexao BI dedicada nao validada", e);
        }
    }

    public static final class BiReadClient implements AutoCloseable {
        private final HikariDataSource pool;
        private final NamedParameterJdbcTemplate jdbc;

        private BiReadClient(HikariDataSource pool) {
            this.pool = pool;
            var template = new org.springframework.jdbc.core.JdbcTemplate(pool);
            template.setQueryTimeout(5);
            this.jdbc = new NamedParameterJdbcTemplate(template);
        }

        public NamedParameterJdbcTemplate jdbc() { return jdbc; }

        @Override public void close() { pool.close(); }
    }

    private static String required(org.springframework.core.env.Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel obrigatoria para BI ausente: " + name);
        }
        return value;
    }
}
