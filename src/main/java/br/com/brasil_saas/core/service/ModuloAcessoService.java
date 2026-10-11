package br.com.brasil_saas.core.service;

import br.com.brasil_saas.core.model.Modulo;
import br.com.brasil_saas.core.model.Usuario;
import br.com.brasil_saas.core.model.UsuarioModulo;
import br.com.brasil_saas.core.repository.ModuloRepository;
import br.com.brasil_saas.core.repository.UsuarioModuloRepository;
import br.com.brasil_saas.core.repository.UsuarioRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Acesso por modulo.
 *
 * Duas camadas independentes:
 * <ul>
 *   <li><b>perfil</b> — o que a pessoa faz dentro do modulo</li>
 *   <li><b>modulo</b> — em quais modulos ela entra</li>
 * </ul>
 *
 * ADMIN e SUPERUSER passam por cima de tudo: quem e admin nao pode ficar sem
 * modulo, senao a mudanca do V91 trancaria o proprio administrador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModuloAcessoService {

    private final ModuloRepository moduloRepository;
    private final UsuarioModuloRepository usuarioModuloRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Modulos visiveis para o usuario.
     *
     * ADMIN e SUPERUSER recebem a lista completa e o seletor nao se aplica a
     * eles: sao o administrador de manutencao e precisam poder chegar em
     * qualquer parte, inclusive no gerenciador SQL. Sem isso, trancar o
     * proprio usuario fora do sistema seria um acidente de um clique.
     */
    public List<Modulo> modulosDoUsuario(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return List.of();

        // A função que veio do AD (no principal da requisição) vale mesmo sem
        // perfil no banco: é o caso do usuário que acaba de completar o
        // cadastro da empresa, ou de um administrador de domínio.
        Authentication principalAuth = SecurityContextHolder.getContext().getAuthentication();
        if (principalAuth != null && principalAuth.getPrincipal() instanceof AuthenticatedUser principal
                && usuarioId.equals(principal.getId())
                && (atravessaEmpresas(principal) || veTudoNaEmpresa(principal))) {
            return moduloRepository.findByAtivoTrueOrderByOrdemAscNomeAsc();
        }

        if (isAdmin(u)) {
            return moduloRepository.findByAtivoTrueOrderByOrdemAscNomeAsc();
        }
        return moduloRepository.findByUsuario(usuarioId);
    }

    /**
     * Os perfis que enxergam TODOS os modulos da propria empresa.
     *
     * <p>O dono definiu: "diretoria e gerente tem que ver todos os modulos da
     * empresa a quem eles pertencem". Eles nao sao administradores do sistema:
     * nao atravessam empresa nenhuma, e o escopo continua sendo o da empresa
     * deles pelo filtro de tenant. O que muda e' so o seletor de modulos — eles
     * nao ficam restritos aos que o administrador marcou.
     *
     * <p>Os nomes ficam aqui, e nao espalhados em condicoes, porque e' a regra
     * de negocio e ela muda: entrar um perfil novo e' acrescentar uma linha.
     */
    private static final Set<String> VEM_TUDO_DENTRO_DA_EMPRESA = Set.of(
            "DIRETORIA",
            "GERENTE",
            "GESTOR"
    );

    /**
     * Verdadeiro se o modulo esta liberado para o usuario da requisicao.
     *
     * <p>E' este metodo que o {@code ModuloAcessoFilter} chama, em TODA
     * requisicao. Por isso a ordem das perguntas e' fixa: os perfis que passam
     * por cima sao decididos pelas <b>authorities do token</b>, que ja estao em
     * memoria, e so quem nao passa chega no banco.
     *
     * <p>Um modulo inexistente devolve {@code false}. E' o que impede um
     * controller novo, em {@code /api/algoQueNaoExiste/}, de passar livre.
     */
    public boolean podeAcessarModulo(AuthenticatedUser usuario, String chaveModulo) {
        if (usuario == null || chaveModulo == null || chaveModulo.isBlank()) {
            return false;
        }
        if (atravessaEmpresas(usuario) || veTudoNaEmpresa(usuario)) {
            return true;
        }
        return moduloRepository.findByChave(chaveModulo)
                .map(m -> moduloRepository.usuarioTemModulo(usuario.getId(), m.getId()))
                .orElse(false);
    }

    /**
     * Verdadeiro se o modulo do usuario e' somente leitura.
     *
     * <p>DIRETORIA e GERENTE "veem todos os modulos", e ver inclui mexer: se
     * fossem somente-leitura em tudo, o bypass de modulo nao valeria para nada.
     */
    public boolean eSomenteLeitura(AuthenticatedUser usuario, String chaveModulo) {
        if (usuario == null || chaveModulo == null || chaveModulo.isBlank()) {
            return false;
        }

        // GERENTE e DIRETORIA têm acesso completo dentro da empresa.
        if (atravessaEmpresas(usuario) || veTudoNaEmpresa(usuario)) {
            return false;
        }

        /*
         * Regra de segurança:
         *   PERFIL -> determina se o módulo abre.
         *   AD ERP_MODULO_<MODULO> -> determina se pode gravar.
         *
         * Portanto o grupo AD nunca abre um módulo sozinho. Ele só transforma
         * em escrita um módulo que o perfil já liberou.
         */
        String grupoGravacao = "erp_modulo_" + chaveModulo.trim().toLowerCase();
        boolean podeGravar = usuario.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(java.util.Objects::nonNull)
                .anyMatch(a -> a.equalsIgnoreCase(grupoGravacao));

        return !podeGravar;
    }

    /**
     * Se o usuario e' DIRETORIA, GERENTE ou GESTOR.
     *
     * <p>Sai das authorities, nao de uma consulta ao perfil: o nome do perfil ja
     * esta no token como {@code ROLE_<funcao>}, entao a resposta vem de memoria.
     * Vale para quem pegou a funcao de um grupo do AD sem ter perfil no banco.
     */
    public boolean veTudoNaEmpresa(AuthenticatedUser usuario) {
        return temPerfil(usuario, VEM_TUDO_DENTRO_DA_EMPRESA);
    }

    /** Se o usuario atravessa empresas: so o SUPERUSER. */
    public boolean atravessaEmpresas(AuthenticatedUser usuario) {
        return temPerfil(usuario, Set.of("SUPERUSER", "SUPERADMIN"));
    }

    private boolean temPerfil(AuthenticatedUser usuario, Set<String> perfis) {
        if (usuario == null || usuario.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority a : usuario.getAuthorities()) {
            String codigo = a.getAuthority();
            if (codigo == null || !codigo.startsWith("ROLE_")) {
                continue;
            }
            if (perfis.contains(codigo.substring("ROLE_".length()).toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verdadeiro se o usuario pode acessar o modulo.
     * Admin e SUPERUSERLiberados para tudo.
     */
    public boolean podeAcessar(Long usuarioId, String chaveModulo) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return false;
        if (isAdmin(u)) return true;

        return moduloRepository.findByUsuario(usuarioId).stream()
                .anyMatch(m -> m.getChave().equalsIgnoreCase(chaveModulo));
    }

    public boolean somenteLeitura(Long usuarioId, String chaveModulo) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return false;
        if (isAdmin(u)) return false;

        // O endpoint /me é chamado pelo próprio usuário autenticado. Quando o
        // principal carrega os grupos do Auth Service, a mesma regra de escrita
        // do filtro HTTP deve ser usada aqui para o frontend receber o estado
        // correto dos botões.
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser atual
                && usuarioId.equals(atual.getId())) {
            return eSomenteLeitura(atual, chaveModulo);
        }

        // Fallback para chamadas internas que não possuem principal HTTP.
        return usuarioModuloRepository.findByUsuarioId(usuarioId).stream()
                .filter(um -> {
                    Modulo m = moduloRepository.findById(um.getModuloId()).orElse(null);
                    return m != null && m.getChave().equalsIgnoreCase(chaveModulo);
                })
                .anyMatch(UsuarioModulo::getSomenteLeitura);
    }

    /** Modulo que guarda documento/certificado exige SUPERUSER, sempre. */
    public boolean podeVerDocumentos(Long usuarioId, String chaveModulo) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return false;
        if (isSuperuser(u)) return true;

        Modulo m = moduloRepository.findByChave(chaveModulo).orElse(null);
        if (m != null && Boolean.TRUE.equals(m.getExigeSuperuser())) {
            log.warn("Usuario {} tentou acessar documento do modulo {} que exige SUPERUSER",
                    u.getUsername(), chaveModulo);
            return false;
        }
        return podeAcessar(usuarioId, chaveModulo);
    }

    /**
     * Substitui o conjunto de modulos do usuario.
     *
     * Substitui, e nao acrescentsa: e o que a tela faz ao salvar a lista de
     * caixas marcadas. Envio vazio deixa sem nenhum modulo — decisao de quem
     * aperta o botao.
     */
    @Transactional
    public void definirModulos(Long usuarioId, List<Long> moduloIds, Map<Long, Boolean> somenteLeituraPorModulo) {

        // ADMIN/SUPERUSER nao sao restringidos pelo seletor (ver modulosDoUsuario)
        Usuario alvo = usuarioRepository.findById(usuarioId).orElse(null);
        if (alvo != null && isAdmin(alvo)) {
            log.info("Usuario {} e ADMIN/SUPERUSER: seletor de modulos ignorado", alvo.getUsername());
            return;
        }

        UsuarioModuloRepository repo = usuarioModuloRepository;
        repo.deleteByUsuarioId(usuarioId);
        repo.flush();

        for (Long moduloId : moduloIds == null ? List.<Long>of() : moduloIds) {
            if (!moduloRepository.existsById(moduloId)) continue;
            UsuarioModulo um = new UsuarioModulo();
            um.setUsuarioId(usuarioId);
            um.setModuloId(moduloId);
            um.setSomenteLeitura(Boolean.TRUE.equals(
                    somenteLeituraPorModulo == null ? null : somenteLeituraPorModulo.get(moduloId)));
            repo.save(um);
        }
    }

    /** Resumo para a tela: todos os modulos + quais o usuario tem. */
    public Map<String, Object> resumo(Long usuarioId) {
        Set<Long> liberados = modulosDoUsuario(usuarioId).stream()
                .map(Modulo::getId).collect(Collectors.toSet());

        Map<Long, Boolean> leitura = usuarioModuloRepository.findByUsuarioId(usuarioId).stream()
                .collect(Collectors.toMap(UsuarioModulo::getModuloId,
                        u -> Boolean.TRUE.equals(u.getSomenteLeitura())));

        List<Map<String, Object>> lista = moduloRepository.findByAtivoTrueOrderByOrdemAscNomeAsc().stream()
                .map(m -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", m.getId());
                    item.put("chave", m.getChave());
                    item.put("nome", m.getNome());
                    item.put("descricao", m.getDescricao());
                    item.put("icone", m.getIcone());
                    item.put("rota", m.getRota());
                    item.put("exigeSuperuser", m.getExigeSuperuser());
                    item.put("liberado", liberados.contains(m.getId()));
                    item.put("somenteLeitura", leitura.getOrDefault(m.getId(), false));
                    return item;
                }).toList();

        return Map.of("modulos", lista, "total", lista.size());
    }

    /**
     * Quem nao e' restringido pelo seletor de modulos.
     *
     * <p><b>O ADMIN saiu na V104</b> e o perfil foi desativado. A condicao
     * aceitava "ADMIN" e "SUPERUSER": aceitar um perfil que nao existe mais e'
     * codigo morto que continua enganando quem le, e da para um
     * usuario novo receber os modulos todos sem ninguem ter decidido isso.
     *
     * <p>Hoje: o SUPERUSER atravessa, e DIRETORIA, GERENTE e GESTOR veem tudo
     * dentro da empresa — a mesma regra do filtro, pelos mesmos motivos.
     */
    private boolean isAdmin(Usuario u) {
        if (u == null || u.getPerfis() == null) {
            return false;
        }
        Set<String> nomes = u.getPerfis().stream()
                .map(perf -> perf.getNome() == null ? "" : perf.getNome().toUpperCase())
                .collect(Collectors.toSet());
        return nomes.contains("SUPERUSER")
                || nomes.contains("SUPERADMIN")
                || nomes.contains("DIRETORIA")
                || nomes.contains("GERENTE")
                || nomes.contains("GESTOR");
    }

    private boolean isSuperuser(Usuario u) {
        return u.getPerfis().stream().anyMatch(p ->
                "SUPERUSER".equalsIgnoreCase(p.getNome()) || "SUPERADMIN".equalsIgnoreCase(p.getNome()));
    }
}
