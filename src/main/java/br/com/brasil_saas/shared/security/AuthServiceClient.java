package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.core.config.AuthServiceProperties;
import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Service
public class AuthServiceClient {

    private final RestClient client;
    private final AuthServiceProperties properties;

    public AuthServiceClient(RestClient.Builder builder, AuthServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        this.client = builder.baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
        this.properties = properties;
    }

    public AuthServiceIdentity authenticate(String username, String password) {
        return authenticate(username, password, "AD");
    }

    public AuthServiceIdentity authenticate(String username, String password, String provider) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Auth Service está desabilitado");
        }

        try {
            AuthServiceIdentity identity = client.post()
                    .uri(properties.getAuthenticatePath())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(username, password, provider))
                    .retrieve()
                    .body(AuthServiceIdentity.class);

            if (identity == null || identity.username() == null || identity.username().isBlank()
                    || !provider.equalsIgnoreCase(identity.provider())) {
                throw new BusinessException("Auth Service não retornou uma identidade válida",
                        "AUTH_SERVICE_INVALID_RESPONSE");
            }

            return new AuthServiceIdentity(
                    identity.identityId(),
                    identity.username(),
                    identity.provider(),
                    identity.groups() == null ? List.of() : List.copyOf(identity.groups()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new BusinessException("Credenciais inválidas", "INVALID_CREDENTIALS");
            }
            log.error("Auth Service respondeu HTTP {} durante autenticação de '{}'",
                    e.getStatusCode().value(), username);
            throw new BusinessException("Serviço de identidade indisponível",
                    "AUTH_SERVICE_UNAVAILABLE");
        } catch (RestClientException e) {
            log.error("Não foi possível consultar o Auth Service para '{}': {}",
                    username, e.getMessage());
            throw new BusinessException("Serviço de identidade indisponível",
                    "AUTH_SERVICE_UNAVAILABLE");
        }
    }

    private record LoginRequest(String username, String password, String provider) {
    }
}
