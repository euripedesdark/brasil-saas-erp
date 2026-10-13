package br.com.brasil_saas.shared.config;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Preenche created_by / updated_by com o id do usuário autenticado.
 */
@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public @NonNull Optional<Long> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.getId());
        }
        return Optional.empty();
    }
}
