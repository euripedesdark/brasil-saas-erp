package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.core.service.ModuloAcessoService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Escopo de leitura do BI de cada pessoa.
 *
 * <p><b>A regra do dono.</b> Quem esta na OU=Diretoria (e o SUPERUSER, os
 * Domain Admins) enxerga todos os dados. Os demais veem so o BI do seu
 * departamento, e o filtro e automatico: sai daqui, no servidor, a partir dos
 * grupos do AD que a pessoa carrega no token.
 *
 * <p><b>Por que o escopo nasce aqui.</b> Se o filtro fosse montado no
 * navegador, bastaria trocar uma variavel na URL para ver o BI inteiro. O
 * escopo e resolvido no servidor e viaja como valor ja fechado.
 */
@Service
@RequiredArgsConstructor
public class BiEscopoService {

    /** As quatro areas do BI, na ordem em que aparecem na tela. */
    public static final List<String> AREAS = List.of("financeiro", "producao", "vendas", "servicos");

    /**
     * Grupo do AD que casa com uma area do BI.
     *
     * <p>E' o mesmo nome do modulo ({@code ERP_MODULO_<MODULO>}) que o
     * {@code ModuloAcessoService} ja usa para definir quem grava em qual
     * modulo. Aqui ele define quem <em>enxerga</em> o BI daquela area.
     */
    private static final Set<String> VER_TUDO_OU = Set.of(
            "GRP_DIRETORIA", "GRP_GERENTE", "GRP_GESTOR",
            "GRP_SUPERUSER", "GRP_ROOT", "GRP_ERP_ADMIN");

    private final ModuloAcessoService moduloAcesso;

    /**
     * As areas que a pessoa pode ver.
     *
     * @return todas as areas para quem e superuser ou esta na OU=Diretoria;
     *         senao, so as areas dos grupos {@code ERP_MODULO_*} da pessoa.
     */
    public List<String> areasVisiveis(AuthenticatedUser usuario) {
        if (usuario == null) {
            return List.of();
        }
        // O SUPERUSER atravessa empresa; os perfis de gestao veem tudo na
        // empresa deles. E' a mesma regra que ja vale para os modulos.
        if (moduloAcesso.atravessaEmpresas(usuario) || moduloAcesso.veTudoNaEmpresa(usuario)) {
            return AREAS;
        }
        if (verTudoPorGrupo(usuario)) {
            return AREAS;
        }
        return areasPorGrupo(usuario);
    }

    public boolean veTodasAsAreas(AuthenticatedUser usuario) {
        return areasVisiveis(usuario).size() == AREAS.size();
    }

    /** Grupo da OU=Diretoria ou de administracao: ve o BI inteiro. */
    private boolean verTudoPorGrupo(AuthenticatedUser usuario) {
        return grupos(usuario).stream().anyMatch(VER_TUDO_OU::contains);
    }

    /** Cada {@code ERP_MODULO_X} libera a area {@code X} do BI. */
    private List<String> areasPorGrupo(AuthenticatedUser usuario) {
        var areas = new TreeSet<String>();
        for (String grupo : grupos(usuario)) {
            if (!grupo.startsWith("erp_modulo_")) {
                continue;
            }
            String area = grupo.substring("erp_modulo_".length());
            if (AREAS.contains(area)) {
                areas.add(area);
            }
        }
        return List.copyOf(areas);
    }

    private Set<String> grupos(AuthenticatedUser usuario) {
        var grupos = new TreeSet<String>();
        for (var authority : usuario.getAuthorities()) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            grupos.add(authority.getAuthority().toLowerCase(Locale.ROOT));
        }
        return grupos;
    }
}
