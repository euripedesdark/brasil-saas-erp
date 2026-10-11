package br.com.brasil_saas.core.service;

import br.com.brasil_saas.core.model.Empresa;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.shared.security.SecretCipher;
import com.stripe.model.Account;
import com.stripe.exception.StripeException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.ApiConnectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaStripeService {
    private final EmpresaRepository empresaRepository;
    private final SecretCipher secretCipher;

    public record ConfigResponse(
        boolean configurada,
        boolean habilitada,
        String chaveMascarada,
        boolean webhookConfigurado,
        String stripeAccountId
    ) {}

    public record ConfigRequest(
        String secretKey,
        String webhookSecret,
        Boolean habilitada
    ) {}

    public record TestResponse(
        boolean sucesso,
        String stripeAccountId,
        String nome,
        String email
    ) {}

    @Transactional(readOnly = true)
    public ConfigResponse obter(Long empresaId) {
        Empresa e = empresa(empresaId);
        String key = secretCipher.decrypt(e.getStripeSecretKeyEncrypted());
        String webhook = secretCipher.decrypt(e.getStripeWebhookSecretEncrypted());
        return new ConfigResponse(
            key != null && !key.isBlank(),
            Boolean.TRUE.equals(e.getStripeHabilitada()),
            mascarar(key),
            webhook != null && !webhook.isBlank(),
            e.getStripeAccountId()
        );
    }

    @Transactional
    public ConfigResponse salvar(Long empresaId, ConfigRequest request) {
        Empresa e = empresa(empresaId);

        if (request.secretKey() != null && !request.secretKey().isBlank()) {
            e.setStripeSecretKeyEncrypted(secretCipher.encrypt(request.secretKey().trim()));
            e.setStripeAccountId(null);
        }
        if (request.webhookSecret() != null && !request.webhookSecret().isBlank()) {
            e.setStripeWebhookSecretEncrypted(secretCipher.encrypt(request.webhookSecret().trim()));
        }
        if (request.habilitada() != null) {
            e.setStripeHabilitada(request.habilitada());
        }

        empresaRepository.save(e);
        return obter(empresaId);
    }

    @Transactional
    public TestResponse testar(Long empresaId) {
        Empresa e = empresa(empresaId);
        String key = secretCipher.decrypt(e.getStripeSecretKeyEncrypted());
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("A empresa ainda não possui uma chave secreta da Stripe");
        }

        try {
            Account account = Account.retrieve(com.stripe.net.RequestOptions.builder().setApiKey(key).build());
            e.setStripeAccountId(account.getId());
            empresaRepository.save(e);
            return new TestResponse(
                true,
                account.getId(),
                account.getBusinessProfile() == null ? null : account.getBusinessProfile().getName(),
                account.getEmail()
            );
        } catch (AuthenticationException ex) {
            // Somente aqui a credencial e mesmo invalida. Os demais casos abaixo
            // sao erro temporario e NAO podem levar o usuario a apagar a chave.
            throw new IllegalStateException("A chave Stripe da empresa foi recusada: " + ex.getMessage(), ex);
        } catch (InvalidRequestException ex) {
            throw new IllegalStateException("A Stripe recusou a requisicao de teste: " + ex.getMessage(), ex);
        } catch (RateLimitException ex) {
            throw new IllegalStateException("Limite de requisicoes da Stripe atingido. Tente de novo em instantes; a chave continua valida.", ex);
        } catch (ApiConnectionException ex) {
            throw new IllegalStateException("Nao foi possivel falar com a Stripe agora. Tente de novo; a chave continua valida.", ex);
        } catch (StripeException ex) {
            throw new IllegalStateException("Falha inesperada na Stripe: " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public String secretKey(Long empresaId) {
        Empresa e = empresa(empresaId);
        String key = secretCipher.decrypt(e.getStripeSecretKeyEncrypted());
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Stripe não configurado para a empresa atual");
        }
        if (!Boolean.TRUE.equals(e.getStripeHabilitada())) {
            throw new IllegalStateException("Stripe está desabilitado para a empresa atual");
        }
        return key;
    }

    @Transactional(readOnly = true)
    public String webhookSecret(Long empresaId) {
        Empresa e = empresa(empresaId);
        String secret = secretCipher.decrypt(e.getStripeWebhookSecretEncrypted());
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Webhook Stripe não configurado para a empresa atual");
        }
        return secret;
    }

    private Empresa empresa(Long empresaId) {
        if (empresaId == null) throw new IllegalArgumentException("Empresa não informada");
        return empresaRepository.findById(empresaId)
            .orElseThrow(() -> new IllegalArgumentException("Empresa não encontrada"));
    }

    private static String mascarar(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 8) return "••••••••";
        return value.substring(0, 4) + "••••••••" + value.substring(value.length() - 4);
    }
}
