package br.com.brasil_saas.core.controller;

import br.com.brasil_saas.core.service.ModuloAcessoService;
import br.com.brasil_saas.core.service.PermissionService;
import br.com.brasil_saas.core.config.IdentidadeProperties;
import br.com.brasil_saas.core.model.Perfil;
import br.com.brasil_saas.core.model.Usuario;
import br.com.brasil_saas.core.repository.EmpresaRepository;
import br.com.brasil_saas.core.repository.PerfilRepository;
import br.com.brasil_saas.core.repository.UsuarioRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Gestao de usuarios: perfis e modulos.
 *
 * ADMIN e SUPERUSER sao equivalentes por decisao (migration V89): SUPERUSER
 * existe para que a manutencao do banco nao dependa do usuario postgres, mas
 * nao tira acesso de quem era ADMIN. Por isso as rotas aceitam os dois — com
 * so ADMIN, o usuario sysdba era barrado da propria tela de administracao.
 *
 * A tela de Usuarios mexe nos dois eixos — perfil diz o que a pessoa faz,
 * modulo diz onde ela entra. Um usuario que opera Financeiro e Estoque nao
 * precisa de um perfil novo, so dos dois modulos marcados.
 */
@RestController
@RequestMapping("/api/superadmin/usuarios")
@RequiredArgsConstructor
public class UsuarioAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final EmpresaRepository empresaRepository;
    private final PermissionService permissionService;
    private final ModuloAcessoService moduloService;
    private final PasswordEncoder passwordEncoder;
    private final IdentidadeProperties identidade;

    /**
     * A lista de usuarios.
     *
     * <p><b>O SUPERUSER ve todas as empresas; os demais veem a sua.</b>
     *
     * <p>Era {@code usuarioRepository.findAll()}, que devolvia a lista inteira
     * para qualquer ADMIN — e a entidade {@code Usuario} ia no corpo com o
     * {@code senhaHash}. Dois problemas no mesmo metodo: o hash vazava, e um
     * administrador de uma empresa enumerava os usuarios de todas as outras.
     *
     * <p><b>Por que o filtro e' por {@code ROLE_SUPERUSER} e nao por
     * "tem perfil de administracao".</b> O migration V89 diz que SUPERUSER
     * herda de ADMIN, mas o SQL das authorities
     * ({@code CustomUserDetailsService.SQL_AUTHORITIES}) le os perfis
     * diretamente atribuidos em {@code bc_core_usuario_perfil} e nao percorre o
     * {@code perfil_pai_id}. Entao um usuario com o perfil SUPERUSER tem
     * {@code ROLE_SUPERUSER} e nenhum outro, e um ADMIN puro tem so
     * {@code ROLE_ADMIN}. Filtrar por {@code ROLE_SUPERUSER} da exatamente a
     * regra desejada sem precisar mexer em perfil nenhum.
     *
     * <p>Um ADMIN sem empresa ve a lista vazia. Isso e' o certo: sem empresa
     * ele nao tem sobre o que ser administrador.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<List<Usuario>> listar(
            @AuthenticationPrincipal AuthenticatedUser usuario) {

        if (isSuperuser(usuario)) {
            return ResponseEntity.ok(usuarioRepository.findAll());
        }

        Long empresaId = usuario == null ? null : usuario.getEmpresaId();
        if (empresaId == null) {
            // Nao vira findAll() "por seguranca": um ADMIN sem empresa veria a
            // lista de todo mundo, que e' exatamente o vazamento que este
            // metodo corrige.
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(usuarioRepository.findAllByEmpresaIdAndDeletedAtIsNull(empresaId));
    }

    /**
     * O unico papel que atravessa empresas: ve e altera dados de todas.
     *
     * <p>SUPERADMIN entra junto porque ja vinha nas {@code @PreAuthorize} deste
     * controller. Nao ha perfil SUPERADMIN cadastrado, entao hoje ele nao
     * concede nada a ninguem — se um dia existir, ele entra por aqui e nao
     * pelos outros metodos.
     */
    private boolean isSuperuser(AuthenticatedUser usuario) {
        if (usuario == null || usuario.getAuthorities() == null) {
            return false;
        }
        return usuario.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .anyMatch(a -> "ROLE_SUPERUSER".equals(a) || "ROLE_SUPERADMIN".equals(a));
    }

    @GetMapping("/{id}/modulos")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> modulos(@PathVariable Long id) {
        return ResponseEntity.ok(moduloService.resumo(id));
    }

    /**
     * Salva o conjunto de modulos. Substitui o que existia.
     * O corpo traz somente os modulos marcados na tela.
     */
    @PostMapping("/{id}/modulos")
    @Transactional
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> salvarModulos(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody ModulosRequest request) {

        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));

        if (Boolean.FALSE.equals(alvo.getAtivo())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "erro", "Usuario inativo nao pode ter modulos alterados"));
        }

        moduloService.definirModulos(id, request.moduloIds(), request.somenteLeitura());
        return ResponseEntity.ok(moduloService.resumo(id));
    }

    // ---------------- criar, alterar, remover ----------------

    /**
     * Os nomes de perfil disponiveis na empresa, para a tela de cadastro.
     */
    @GetMapping("/perfis-disponiveis")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<List<String>> perfisDisponiveis(
            @AuthenticationPrincipal AuthenticatedUser usuario) {

        Long empresaId = isSuperuser(usuario)
                ? empresaDoSuperuser(usuario)
                : (usuario == null ? null : usuario.getEmpresaId());

        if (empresaId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(perfilRepository.listarNomesPorEmpresa(empresaId));
    }

    /**
     * Cria um usuario.
     *
     * <p><b>Por que este metodo nao existia.</b> A tela de Usuarios so
     * listava, trocava modulos e dava perfil. Nao havia {@code @PostMapping}
     * de criacao em lugar nenhum do projeto, entao o formulario da tela
     * chamava uma rota inexistente: o usuario nao era gravado, nao aparecia na
     * listagem e, logicamente, nao entrava — porque nunca chegou a existir.
     * Isso e' o que produz "criei o usuario e ele nao loga".
     *
     * <p><b>A empresa.</b> O SUPERUSER pode escolher a empresa do corpo da
     * requisicao. Um ADMIN nao: ele so cria na empresa dele, e mandar outra
     * empresa no corpo e' recusado em vez de silenciosamente ignorado.
     *
     * <p><b>A senha.</b> Vem em texto no corpo, e vai para o banco como BCrypt.
     * A senha inicial nao volta na resposta — {@code senhaHash} tem
     * {@code @JsonIgnore} na entidade.
     */
    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Usuario> criar(
            @AuthenticationPrincipal AuthenticatedUser executor,
            @RequestBody CriarUsuarioRequest request) {

        String username = request.username() == null ? null : request.username().trim();
        String senha = request.senha();

        if (username == null || username.isBlank()) {
            throw new BusinessException("O nome de usuário é obrigatório");
        }
        if (senha == null || senha.length() < 6) {
            throw new BusinessException("A senha precisa de pelo menos 6 caracteres");
        }
        if (usuarioRepository.findByUsernameWithAuthorities(username).isPresent()) {
            throw new BusinessException("Já existe um usuário com esse nome de usuário");
        }

        Long empresaId = resolverEmpresaParaGravacao(executor, request.empresaId());

        Usuario novo = new Usuario();
        novo.setNome(request.nome() == null || request.nome().isBlank() ? username : request.nome().trim());
        novo.setUsername(username);
        novo.setEmail(request.email() == null || request.email().isBlank()
                ? identidade.emailPara(username)
                : request.email().trim());
        novo.setSenhaHash(passwordEncoder.encode(senha));
        novo.setAtivo(request.ativo() == null || request.ativo());
        novo.setMfaHabilitado(false);
        novo.setTentativasLogin(0);

        Usuario salvo = usuarioRepository.save(novo);
        atribuirPerfis(salvo, request.perfil(), request.perfilIds(), executor);
        return ResponseEntity.ok(usuarioRepository.findById(salvo.getId()).orElse(salvo));
    }

    /**
     * Altera o que da para alterar: nome, email, se esta ativo, a empresa e os
     * perfis. A senha nao entra aqui — trocar senha tem rota propria, que exige
     * a senha nova sem a antiga, porque quem pode alterar usuario nem sempre
     * conhece a senha dele.
     */
    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Usuario> atualizar(
            @AuthenticationPrincipal AuthenticatedUser executor,
            @PathVariable Long id,
            @RequestBody AtualizarUsuarioRequest request) {

        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        exigirMesmaEmpresa(executor, alvo);

        if (request.nome() != null && !request.nome().isBlank()) {
            alvo.setNome(request.nome().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            alvo.setEmail(request.email().trim());
        }
        if (request.ativo() != null) {
            if (Boolean.FALSE.equals(request.ativo())) {
                exigirNaoSerUltimoSuperuser(alvo);
            }
            alvo.setAtivo(request.ativo());
        }
        if (request.empresaId() != null && !request.empresaId().equals(alvo.getEmpresaId())) {
            if (!isSuperuser(executor)) {
                throw new BusinessException("Só o SUPERUSER move um usuário para outra empresa");
            }
            alvo.setEmpresaId(request.empresaId());
        }

        Usuario salvo = usuarioRepository.save(alvo);
        if (request.perfil() != null || request.perfilIds() != null) {
            atribuirPerfis(salvo, request.perfil(), request.perfilIds(), executor);
        }
        return ResponseEntity.ok(usuarioRepository.findById(salvo.getId()).orElse(salvo));
    }

    /**
     * Troca a senha de um usuario.
     *
     * <p>Existe separada do PUT porque quem administra nao conhece a senha do
     * outro. E zera o contador de tentativas, senao um usuario bloqueado por
     * tentativas erradas continua bloqueado depois da troca.
     */
    @PutMapping("/{id}/senha")
    @Transactional
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> trocarSenha(
            @AuthenticationPrincipal AuthenticatedUser executor,
            @PathVariable Long id,
            @RequestBody TrocarSenhaAdminRequest request) {

        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        exigirMesmaEmpresa(executor, alvo);

        if (request.novaSenha() == null || request.novaSenha().length() < 6) {
            throw new BusinessException("A senha precisa de pelo menos 6 caracteres");
        }

        alvo.setSenhaHash(passwordEncoder.encode(request.novaSenha()));
        alvo.setTentativasLogin(0);
        usuarioRepository.save(alvo);

        return ResponseEntity.ok(Map.of("mensagem", "Senha alterada"));
    }

    /**
     * Remove um usuario.
     *
     * <p><b>E desativa, nao apaga.</b> O registro vira {@code ativo = false} em
     * vez de sumir, porque usuario tem historico espalhado em nota fiscal,
     * venda, compra e auditoria: apagar a linha deixaria essas referencias
     * apontando para o nada. Desativado, ele para de entrar e continua
     * nomeado no historico, que e o que a auditoria precisa.
     *
     * <p><b>Duas travas.</b> Nao se apaga a si mesmo, e nao se apaga o ultimo
     * superuser ativo — nas duas sao os casos em que o resultado e' um sistema
     * sem ninguem capaz de administeredar.
     */
    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> remover(
            @AuthenticationPrincipal AuthenticatedUser executor,
            @PathVariable Long id) {

        Usuario alvo = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (executor != null && id.equals(executor.getId())) {
            throw new BusinessException("Você não pode remover o seu próprio usuário");
        }
        if (!isSuperuser(executor)) {
            exigirMesmaEmpresa(executor, alvo);
        }
        exigirNaoSerUltimoSuperuser(alvo);

        alvo.setAtivo(false);
        usuarioRepository.save(alvo);

        return ResponseEntity.ok(Map.of(
                "mensagem", "Usuário desativado",
                "usuarioId", id));
    }

    // ---------------- apoio dos endpoints acima ----------------

    /**
     * A empresa que o novo usuario vai ter.
     *
     * <p>O SUPERUSER escolhe no corpo. O ADMIN nao escolhe: cai na empresa
     * dele, e um {@code empresaId} diferente e' recusado com mensagem, nao
     * ignorado em silencio — um cadastro que cai na empresa errada sem avisar
     * e' pior do que um cadastro que falha.
     */
    private Long resolverEmpresaParaGravacao(AuthenticatedUser executor, Long empresaIdPedida) {
        if (isSuperuser(executor)) {
            if (empresaIdPedida != null) {
                return empresaIdPedida;
            }
            Long empresaDoSuperuser = empresaDoSuperuser(executor);
            if (empresaDoSuperuser == null) {
                throw new BusinessException("O SUPERUSER precisa informar a empresa do usuário");
            }
            return empresaDoSuperuser;
        }

        Long empresaDoAdmin = executor == null ? null : executor.getEmpresaId();
        if (empresaDoAdmin == null) {
            throw new BusinessException("Usuário sem empresa não pode cadastrar outros usuários");
        }
        if (empresaIdPedida != null && !empresaIdPedida.equals(empresaDoAdmin)) {
            throw new BusinessException("Você só pode cadastrar usuário na sua própria empresa");
        }
        return empresaDoAdmin;
    }

    /**
     * A empresa do superuser, pelo id do token.
     *
     * <p>Um superuser sem empresa e' situacao legitima: e' o superuser que
     * administra as empresas. Nesses casos cai na primeira empresa, que e' o
     * comportamento do {@code ensurePostgresSuperuser}.
     */
    private Long empresaDoSuperuser(AuthenticatedUser usuario) {
        if (usuario == null) {
            return null;
        }
        if (usuario.getEmpresaId() != null) {
            return usuario.getEmpresaId();
        }
        return empresaRepository.findAll().stream()
                .map(e -> e.getId())
                .findFirst()
                .orElse(null);
    }

    /**
     * Um ADMIN so mexe em usuario da propria empresa.
     */
    private void exigirMesmaEmpresa(AuthenticatedUser executor, Usuario alvo) {
        if (isSuperuser(executor)) {
            return;
        }
        Long empresaDoExecutor = executor == null ? null : executor.getEmpresaId();
        Long empresaDoAlvo = alvo.getEmpresaId();
        if (empresaDoExecutor == null) {
            throw new BusinessException("Usuário sem empresa não administra ninguém");
        }
        if (empresaDoAlvo == null || !empresaDoAlvo.equals(empresaDoExecutor)) {
            throw new BusinessException("Você só administra usuários da sua empresa");
        }
    }

    /**
     * Nao deixa o sistema ficar sem superuser.
     *
     * <p>Se o alvo tem SUPERUSER e e' o unico ativo com esse perfil, o pedido
     * e' recusado. Sem isto, o proprio superuser desativa a si mesmo pelo
     * caminho do PUT com {@code ativo: false} e o ERP fica sem ninguem que
     * entre na administracao.
     */
    private void exigirNaoSerUltimoSuperuser(Usuario alvo) {
        if (!isSuperuserDoBanco(alvo)) {
            return;
        }
        if (usuarioRepository.contarSuperusersAtivos() <= 1) {
            throw new BusinessException(
                    "Este é o único SUPERUSER ativo. Crie outro antes de desativar este.");
        }
    }

    private boolean isSuperuserDoBanco(Usuario usuario) {
        if (usuario.getPerfis() == null) {
            return false;
        }
        return usuario.getPerfis().stream()
                .map(Perfil::getNome)
                .anyMatch(n -> "SUPERUSER".equals(n) || "SUPERADMIN".equals(n));
    }

    /**
     * Aplica os perfis pedidos no cadastro.
     *
     * <p>Aceita as duas formas que a tela pode mandar: {@code perfil} com o nome
     * ("ADMIN"), que e' o que o administrador digita, ou {@code perfilIds} com
     * os ids, que e' o que uma tela com seletor ja resolvido manda. A busca do
     * perfil por nome e' sempre dentro da empresa do usuario, pelo motivo que
     * o {@code PerfilRepository} explica.
     */
    private void atribuirPerfis(Usuario usuario, String perfil, List<Long> perfilIds,
                                AuthenticatedUser executor) {

        Usuario executorDb = executor == null ? null
                : usuarioRepository.findById(executor.getId()).orElse(null);

        if (perfil != null && !perfil.isBlank()) {
            Perfil p = perfilRepository
                    .findByNomeAndEmpresaId(perfil.trim().toUpperCase(), usuario.getEmpresaId())
                    .orElseThrow(() -> new BusinessException(
                            "Perfil " + perfil + " não existe nesta empresa"));
            permissionService.atribuirPerfil(usuario, p, executorDb);
        }

        if (perfilIds != null) {
            for (Long perfilId : perfilIds) {
                Perfil p = perfilRepository.findById(perfilId)
                        .orElseThrow(() -> new BusinessException("Perfil " + perfilId + " não existe"));
                if (!p.getEmpresaId().equals(usuario.getEmpresaId())) {
                    throw new BusinessException(
                            "O perfil " + perfilId + " é de outra empresa");
                }
                permissionService.atribuirPerfil(usuario, p, executorDb);
            }
        }
    }

    // ---------------- perfis (existente) ----------------

    @PostMapping("/permissao")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> updatePermission(@RequestBody PermissionUpdateRequest request) {
        Usuario target = usuarioRepository.findById(request.usuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Perfil perfil = perfilRepository.findById(request.perfilId())
                .orElseThrow(() -> new RuntimeException("Perfil não encontrado"));

        Usuario executor = usuarioRepository.findById(request.executorId())
                .orElseThrow(() -> new RuntimeException("Executor não encontrado"));

        if (request.adicionar()) {
            permissionService.atribuirPerfil(target, perfil, executor);
        } else {
            permissionService.removerPerfil(target, perfil, executor);
        }

        return ResponseEntity.ok("Permissão atualizada");
    }

    public record ModulosRequest(List<Long> moduloIds, Map<Long, Boolean> somenteLeitura) {
    }

    public record PermissionUpdateRequest(Long usuarioId, Long perfilId, boolean adicionar, Long executorId) {
    }

    /**
     * O corpo do cadastro.
     *
     * <p>{@code empresaId} so e' usado pelo SUPERUSER. {@code perfil} e' o nome
     * que o administrador digita; {@code perfilIds} e' para a tela que ja
     * tem o seletor resolvido. Mandar os dois e' valido.
     */
    public record CriarUsuarioRequest(
            String username,
            String nome,
            String email,
            String senha,
            Long empresaId,
            String perfil,
            List<Long> perfilIds,
            Boolean ativo) {
    }

    /**
     * O corpo da alteracao. Tudo opcional: o que vier nulo nao muda.
     */
    public record AtualizarUsuarioRequest(
            String nome,
            String email,
            Long empresaId,
            String perfil,
            List<Long> perfilIds,
            Boolean ativo) {
    }

    /**
     * A troca de senha feita por quem administra. Nao pede a senha antiga,
     * porque quem administra nao conhece a senha do outro.
     */
    public record TrocarSenhaAdminRequest(String novaSenha) {
    }
}
