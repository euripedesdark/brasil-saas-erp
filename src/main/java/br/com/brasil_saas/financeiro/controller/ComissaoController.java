package br.com.brasil_saas.financeiro.controller;

import br.com.brasil_saas.financeiro.dto.ComissaoDtos.RegraRequest;
import br.com.brasil_saas.financeiro.model.Comissao;
import br.com.brasil_saas.financeiro.service.ComissaoService;
import br.com.brasil_saas.cadastro.model.Pessoa;
import br.com.brasil_saas.cadastro.repository.PessoaRepository;
import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.vendas.model.RegraComissao;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comissao do vendedor.
 *
 * A tela (rota /financeiro/comissoes) existia e chamava estes dois endpoints
 * desde sempre; nao havia controller, entao as chamadas davam 404 e a tela
 * ficava em branco sem mensagem — o erro era engolido por um alert.
 */
@RestController
@RequestMapping("/api/financeiro/comissoes")
@RequiredArgsConstructor
public class ComissaoController {

    private final ComissaoService service;
    private final FuncionarioRepository funcionarioRepository;
    private final PessoaRepository pessoaRepository;

    /** Tenant do token, nunca da query: mesma regra do PedidoVendaController. */
    private Long empresa(AuthenticatedUser user) {
        if (user == null || user.getEmpresaId() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Usuario sem empresa definida no token");
        }
        return user.getEmpresaId();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<List<Map<String, Object>>> listar(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long funcionarioId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        Long empresaId = empresa(user);
        Map<Long, String> nomes = nomeDosFuncionarios(empresaId);

        List<Map<String, Object>> resposta = service.listar(empresaId, status, funcionarioId)
                .stream()
                .map(c -> paraMapa(c, nomes))
                .toList();
        return ResponseEntity.ok(resposta);
    }

    @PostMapping("/{id}/pagar")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> pagar(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long empresaId = empresa(user);
        Comissao comissao = service.pagar(id, empresaId);
        return ResponseEntity.ok(paraMapa(comissao, nomeDosFuncionarios(empresaId)));
    }

    @GetMapping("/resumo")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> resumo(
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long empresaId = empresa(user);
        List<Comissao> todas = service.listar(empresaId, null, null);

        BigDecimal pendente = soma(todas.stream()
                .filter(c -> "PENDENTE".equalsIgnoreCase(c.getStatus()))
                .map(Comissao::getValorComissao).toList());
        BigDecimal pago = soma(todas.stream()
                .filter(c -> "PAGO".equalsIgnoreCase(c.getStatus()))
                .map(Comissao::getValorComissao).toList());

        Map<String, Object> mapa = new HashMap<>();
        mapa.put("totalPendente", pendente);
        mapa.put("totalPago", pago);
        mapa.put("quantidadePendente", todas.stream()
                .filter(c -> "PENDENTE".equalsIgnoreCase(c.getStatus())).count());
        mapa.put("quantidadePago", todas.stream()
                .filter(c -> "PAGO".equalsIgnoreCase(c.getStatus())).count());
        return ResponseEntity.ok(mapa);
    }

    // ------------------------------------------------------------------ regras

    /**
     * Regras de comissao.
     *
     * Sem nenhuma regra cadastrada, PedidoVendaServiceImpl cai no
     * {@code orElse(BigDecimal.ZERO)} e grava a comissao como 0,00% — sem
     * erro e sem aviso. Estas rotas sao o que impede isso de continuar: a
     * regra passa a ter onde ser cadastrada.
     */
    @GetMapping("/regras")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<List<Map<String, Object>>> listarRegras(
            @RequestParam(required = false) Long vendedorId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(service.listarRegras(empresa(user), vendedorId)
                .stream().map(ComissaoController::paraMapa).toList());
    }

    @PostMapping("/regras")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> criarRegra(
            @Valid @RequestBody RegraRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201)
                .body(paraMapa(service.criarRegra(request, empresa(user))));
    }

    @PutMapping("/regras/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> atualizarRegra(
            @PathVariable Long id,
            @Valid @RequestBody RegraRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(paraMapa(service.atualizarRegra(id, request, empresa(user))));
    }

    @DeleteMapping("/regras/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','SUPERUSER')")
    public ResponseEntity<Void> excluirRegra(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.excluirRegra(id, empresa(user));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Nome do vendedor para a listagem.
     *
     * Funcionario nao tem nome proprio: ele aponta para Pessoa. O nome e
     * cosmetico, entao qualquer falha aqui devolve mapa vazio em vez de
     * derrubar a listagem.
     */
    private Map<Long, String> nomeDosFuncionarios(Long empresaId) {
        Map<Long, String> nomes = new HashMap<>();
        try {
            List<Long> idsPessoa = funcionarioRepository
                    .findByEmpresaIdAndAtivoTrue(empresaId).stream()
                    .map(Funcionario::getPessoaId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!idsPessoa.isEmpty()) {
                pessoaRepository.findAllById(idsPessoa).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Pessoa::getId, Pessoa::getNome, (a, b) -> a))
                        .forEach((idPessoa, nome) -> {
                            // monta funcionario -> nome
                            funcionarioRepository.findByEmpresaIdAndAtivoTrue(empresaId).stream()
                                    .filter(f -> idPessoa.equals(f.getPessoaId()))
                                    .forEach(f -> nomes.put(f.getId(), nome));
                        });
            }
        } catch (RuntimeException e) {
            nomes.clear();
        }
        return nomes;
    }

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream()
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<String, Object> paraMapa(Comissao c, Map<Long, String> nomes) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("funcionarioId", c.getFuncionarioId());
        m.put("funcionarioNome", nomes.get(c.getFuncionarioId()));
        m.put("pedidoId", c.getPedidoId());
        m.put("valorVenda", c.getValorVenda());
        m.put("percentual", c.getPercentual());
        m.put("valorComissao", c.getValorComissao());
        m.put("status", c.getStatus());
        m.put("dataPagamento", c.getDataPagamento());
        return m;
    }

    private static Map<String, Object> paraMapa(RegraComissao r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("nome", r.getNome());
        m.put("vendedorId", r.getVendedorId());
        m.put("vigenciaInicio", r.getVigenciaInicio() == null ? null : r.getVigenciaInicio().atStartOfDay());
        m.put("vigenciaFim", r.getVigenciaFim() == null ? null : r.getVigenciaFim().atStartOfDay());
        m.put("metaValor", r.getMetaValor());
        m.put("faixaValorMin", r.getFaixaValorMin());
        m.put("faixaValorMax", r.getFaixaValorMax());
        m.put("percentual", r.getPercentual());
        m.put("baseCalculo", r.getBaseCalculo());
        m.put("ativo", r.getAtivo());
        return m;
    }
}
