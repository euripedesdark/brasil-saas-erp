package br.com.brasil_saas.bi.service;

import br.com.brasil_saas.core.service.ModuloAcessoService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p><b>De onde vem o departamento.</b> O AD tem dois jeitos de marcar o
 * departamento e os dois valem: o grupo tecnico {@code ERP_MODULO_<X>} e o
 * grupo de negocio {@code GRP_<X>} que vive na OU do departamento. Ambos
 * viram a area {@code <X>} do BI.
 */
@Service
@RequiredArgsConstructor
public class BiEscopoService {

    /** As quatro areas do BI, na ordem em que aparecem na tela. */
    public static final List<String> AREAS = List.of("financeiro", "producao", "vendas", "servicos");

    /**
     * Quem ve o BI inteiro, por grupo do AD.
     *
     * <p>Sao os grupos de administracao e de diretao. Um usuario comum que
     * caia aqui passaria a enxergar o BI de areas que nao sao dele, e por isso
     * a lista e curta e explicita.
     */
    private static final Set<String> VER_TUDO = Set.of(
            "grp_diretoria",   // OU=Diretoria
            "grp_gerente",     // OU=Gestao
            "grp_gestor",      // OU=Gestao
            "grp_superuser",   // OU=Administracao
            "grp_root",        // OU=Tecnologia
            "grp_erp_admin",
            "domain admins",
            "administrators");

    /**
     * Grupo de negocio que define o departamento.
     *
     * <p>O nome do grupo e o nome da area. {@code GRP_VENDEDOR} fica em
     * {@code OU=Vendas}, {@code GRP_FINANCEIRO} em {@code OU=Financeiro}, e
     * assim por diante. O prefixo {@code GRP_} e retirado para casar.
     */
    private static final Map<String, String> DEPARTAMENTO = Map.ofEntries(
            Map.entry("grp_financeiro", "financeiro"),
            Map.entry("grp_vendedor", "vendas"),
            Map.entry("grp_estoque", "producao"),
            Map.entry("grp_servicos", "servicos"),
            Map.entry("grp_sa", "producao"),        // quem opera a maquina ve a operacao
            Map.entry("grp_rh", "producao"),        // gente: pessoa & producao
            Map.entry("grp_fiscal", "financeiro"),  // fiscal acompanha o dinheiro
            Map.entry("grp_admin", "financeiro"),   // administracao ve o caixa
            Map.entry("grp_dbm_admin", "producao"),
            Map.entry("grp_dbm_operador", "producao"),
            Map.entry("grp_astral_admin", "producao"),
            Map.entry("grp_postgres", "producao"));

    private final ModuloAcessoService moduloAcesso;

    /**
     * As areas que a pessoa pode ver.
     *
     * @return todas as areas para quem e superuser ou esta na OU=Diretoria;
     *         senao, so as areas do departamento da pessoa.
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
        Set<String> grupos = grupos(usuario);
        if (grupos.stream().anyMatch(VER_TUDO::contains)) {
            return AREAS;
        }
        return areasDoDepartamento(grupos);
    }

    public boolean veTodasAsAreas(AuthenticatedUser usuario) {
        return areasVisiveis(usuario).size() == AREAS.size();
    }

    /**
     * O departamento da pessoa, seja pelo grupo tecnico do modulo, seja pelo
     * grupo de negocio do AD.
     */
    private List<String> areasDoDepartamento(Set<String> grupos) {
        var areas = new TreeSet<String>();
        for (String grupo : grupos) {
            // ERP_MODULO_<X> -> <X>
            if (grupo.startsWith("erp_modulo_")) {
                String area = grupo.substring("erp_modulo_".length());
                if (AREAS.contains(area)) {
                    areas.add(area);
                }
                continue;
            }
            // GRP_<X> na OU do departamento -> <X>
            String area = DEPARTAMENTO.get(grupo);
            if (area != null) {
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
