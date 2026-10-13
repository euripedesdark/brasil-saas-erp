package br.com.brasil_saas.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

@Slf4j
@Service
public class PostgresRoleAuthenticationService {

    private final DataSourceProperties dataSourceProperties;

    public PostgresRoleAuthenticationService(DataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    /**
     * Autentica uma role PostgreSQL diretamente com usuário + senha.
     *
     * Nesta fase o canal continua criptografado (SSL), mas a autenticação
     * da role administrativa não depende de certificado de cliente.
     *
     * O hardening com mTLS/cliente por role poderá ser aplicado depois.
     */
    public boolean authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return false;
        }

        try (Connection connection = openRoleConnection(username, password)) {
            boolean valid = connection.isValid(3);
            if (valid) {
                log.info("Autenticação PostgreSQL concluída para role '{}'", username);
            }
            return valid;
        } catch (SQLException | RuntimeException e) {
            log.warn("Falha na autenticação da role PostgreSQL '{}': SQLState={}, erro={}",
                    username, e instanceof SQLException sql ? sql.getSQLState() : "N/A", e.getMessage());
            return false;
        }
    }

    /**
     * Autentica a própria role PostgreSQL e confirma rolsuper=true.
     *
     * Esta é a única condição que permite que a credencial PostgreSQL
     * substitua o BCrypt do usuário ERP.
     */
    public boolean authenticateSuperuser(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            return false;
        }

        try (Connection connection = openRoleConnection(username, password);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT current_user, current_database(), rolsuper " +
                     "FROM pg_roles WHERE rolname = current_user");
             ResultSet resultSet = statement.executeQuery()) {

            if (!resultSet.next()) {
                log.warn("Role PostgreSQL '{}' autenticou, mas não foi encontrada em pg_roles", username);
                return false;
            }

            boolean superuser = resultSet.getBoolean("rolsuper");

            log.info("Autenticação/verificação PostgreSQL concluída: role='{}', database='{}', rolsuper={}",
                    resultSet.getString("current_user"),
                    resultSet.getString("current_database"),
                    superuser);

            return superuser;
        } catch (SQLException | RuntimeException e) {
            log.warn("Falha na autenticação/verificação de SUPERUSER da role '{}': SQLState={}, erro={}",
                    username, e instanceof SQLException sql ? sql.getSQLState() : "N/A", e.getMessage());
            return false;
        }
    }

    public boolean isSuperuser(String username, String password) {
        return authenticateSuperuser(username, password);
    }

    private Connection openRoleConnection(String username, String password) throws SQLException {
        String jdbcUrl = dataSourceProperties.getUrl();

        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw new SQLException("URL JDBC PostgreSQL não configurada");
        }

        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);

        /*
         * Fase atual:
         * - SSL continua obrigatório;
         * - não usamos sslcert/sslkey da role técnica "sa";
         * - não exigimos certificado de cliente para a role SUPERUSER.
         *
         * O psql -U euripedes -h localhost -d brasil-saas já comprovou
         * que a role aceita autenticação por senha no PostgreSQL.
         */
        properties.setProperty("sslmode", "require");

        String authenticationUrl = removeCertificateOptions(jdbcUrl);
        authenticationUrl = forceSslModeRequire(authenticationUrl);

        return DriverManager.getConnection(authenticationUrl, properties);
    }

    private String removeCertificateOptions(String url) {
        return url
                .replaceAll("(?i)([?&])sslcert=[^&]*&?", "")
                .replaceAll("(?i)([?&])sslkey=[^&]*&?", "")
                .replaceAll("(?i)([?&])sslrootcert=[^&]*&?", "")
                .replaceAll("[?&]+$", "");
    }

    /**
     * A conexão da role informada pelo usuário usa SSL, mas não reutiliza
     * sslmode=verify-ca nem qualquer certificado da role técnica "sa".
     * Isso evita que o driver procure ~/.postgresql/root.crt.
     */
    private String forceSslModeRequire(String url) {
        if (url.matches("(?i).*([?&])sslmode=[^&]*.*")) {
            return url.replaceAll("(?i)([?&])sslmode=[^&]*", "$1sslmode=require");
        }

        return url + (url.contains("?") ? "&" : "?") + "sslmode=require";
    }
}
