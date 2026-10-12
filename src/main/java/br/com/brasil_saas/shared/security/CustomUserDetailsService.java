package br.com.brasil_saas.shared.security;

import br.com.brasil_saas.core.model.Usuario;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.core.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Carrega o usuário e as permissões dele de duas fontes.
 *
 * <p>As permissões existentes no Postgres continuam sendo carregadas como antes.
 * Os grupos recebidos do Auth Service são traduzidos somente pelos mapeamentos
 * explícitos de AD abaixo. Um grupo que não esteja no contrato não vira uma
 * authority do Spring Security.
 */
@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository, EmpresaRepository empresaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadUserByUsername(username, List.of());
    }

    /**
     * Carrega o usuário usando os grupos que vieram do Auth Service e foram
     * preservados no JWT do ERP. O ERP não consulta o AD diretamente para
     * reconstruir a identidade.
     */
    public AuthenticatedUser loadUserByUsername(String username, Collection<String> authServiceGroups)
            throws UsernameNotFoundException {
        Usuario user = usuarioRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));

        Set<String> authorities = new LinkedHashSet<>();
        user.getPerfis().forEach(pf -> {
            if (pf.getNome() != null) authorities.add("ROLE_" + pf.getNome());
            pf.getPermissoes().forEach(pm -> {
                if (pm.getCodigo() != null) authorities.add(pm.getCodigo());
            });
        });

        Long empresaId = user.getEmpresaId();

        Set<String> grupos = new LinkedHashSet<>();
        if (authServiceGroups != null) {
            authServiceGroups.stream()
                    .filter(g -> g != null && !g.isBlank())
                    .map(g -> g.trim().toLowerCase(Locale.ROOT))
                    .forEach(grupos::add);
        }

        Long empresaDoAd = empresaDoAd(grupos);
        if (empresaDoAd != null) {
            empresaId = empresaDoAd;
        }

        authorities.addAll(authoritiesDosGrupos(grupos));
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getSenhaHash(),
                Boolean.TRUE.equals(user.getAtivo()),
                empresaId,
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
    }

    /**
     * Resolve a empresa pelo grupo {@code ERP_EMPRESA_<cnpj>}, ou retorna null
     * para manter a empresa que o cadastro do usuário já possui.
     */
    private Long empresaDoAd(Set<String> grupos) {
        for (String grupo : grupos) {
            if (grupo.startsWith("erp_empresa_")) {
                String cnpj = grupo.substring("erp_empresa_".length());
                var empresa = empresaRepository.findByCnpj(cnpj);
                if (empresa.isPresent()) {
                    return empresa.get().getId();
                }
                log.warn("Grupo ERP_EMPRESA_{} aponta para um CNPJ que não existe em bc_core_empresa", cnpj);
            }
        }
        return null;
    }

    /**
     * Mapeamento explícito entre grupos do AD e roles reconhecidas pelo ERP.
     * Não há conversão genérica de nomes: somente estes grupos concedem roles.
     *
     * <p>Diretiva do dono (06/10): <b>todo administrador do AD — Administrators
     * ou Domain Admins — é SUPERUSER do ERP</b>, acima das roles do sistema.
     * Os grupos de domínio entram pelo sAMAccountName em minúsculas, que é o
     * que o Auth Service devolve.
     */
    private static final Map<String, String> GRUPOS_PARA_ROLES = Map.ofEntries(
            Map.entry("administrators",  "ROLE_SUPERUSER"),
            Map.entry("domain admins",   "ROLE_SUPERUSER"),
            Map.entry("grp_diretoria",   "ROLE_DIRETORIA"),
            Map.entry("grp_gerente",     "ROLE_GERENTE"),
            Map.entry("grp_gestor",      "ROLE_GESTOR"),
            Map.entry("grp_financeiro",  "ROLE_FINANCEIRO"),
            Map.entry("grp_estoque",     "ROLE_ESTOQUE"),
            Map.entry("grp_rh",          "ROLE_RH"),
            Map.entry("grp_vendedor",    "ROLE_VENDEDOR"),
            Map.entry("grp_erp_admin",   "ROLE_ADMIN"),
            Map.entry("grp_superuser",   "ROLE_SUPERUSER"),
            Map.entry("postgres_superuser", "ROLE_SUPERUSER")
    );

    /** Traduz somente os grupos explicitamente autorizados nas roles do ERP. */
    public static Set<String> authoritiesDosGrupos(Collection<String> grupos) {
        Set<String> roles = new LinkedHashSet<>();
        if (grupos == null) return roles;
        for (String valor : grupos) {
            if (valor == null) continue;
            String grupo = valor.trim().toLowerCase(Locale.ROOT);
            String role = GRUPOS_PARA_ROLES.get(grupo);
            if (role != null) roles.add(role);
            // Marcador de escrita de modulo, reconhecido pelo ModuloAcessoService.
            // Nao concede role nem libera modulo que o perfil nao permite.
            if (grupo.matches("erp_modulo_[a-z0-9_]+")) roles.add(grupo);
        }
        return roles;
    }

    /**
     * O nome da pessoa no AD, a partir do que ela digitou.
     *
     * <p>No Windows a conta aparece das duas formas: {@code euripedes} pelo
     * Windows, {@code SRVCLOUD\\euripedes} em contexto de rede. O
     * {@code sAMAccountName} do AD é só o nome curto, então o prefixo do
     * domínio é removido antes da consulta.
     */
    public static String nomeNoAd(String username) {
        if (username == null) {
            return null;
        }
        int barra = username.indexOf('\\');
        String nome = barra >= 0 ? username.substring(barra + 1) : username;
        int arroba = nome.indexOf('@');
        if (arroba > 0) {
            nome = nome.substring(0, arroba);
        }
        return nome.trim();
    }
}
