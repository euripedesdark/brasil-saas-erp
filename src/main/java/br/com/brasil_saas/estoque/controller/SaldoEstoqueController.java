package br.com.brasil_saas.estoque.controller;

import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import br.com.brasil_saas.shared.security.AuthenticatedUser;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/estoque/saldos")
@RequiredArgsConstructor
public class SaldoEstoqueController {

    private final SaldoEstoqueRepository repository;
    private final DepositoRepository depositoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final ProdutoRepository produtoRepository;

    /**
     * Com produtoId: saldo de um produto.
     * Sem produtoId: todos os saldos da empresa.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:saldo:leitura')")
    public ResponseEntity<?> buscar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long produtoId) {

        if (produtoId != null) {
            return repository.findByEmpresaIdAndProdutoId(user.getEmpresaId(), produtoId)
                    .map(saldo -> ResponseEntity.ok(SaldoResponse.from(saldo)))
                    .orElseGet(() -> ResponseEntity.ok(new SaldoResponse(user.getEmpresaId(), produtoId, BigDecimal.ZERO)));
        }

        List<SaldoResponse> lista = repository.findByEmpresaIdOrderByProdutoIdAsc(user.getEmpresaId()).stream()
                .map(SaldoResponse::from)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Ajuste manual de inventario. O valor informado e um delta: positivo
     * adiciona estoque; negativo baixa estoque. Toda alteracao gera
     * movimentacao auditavel e respeita o tenant do token.
     */
    @PostMapping("/ajustes")
    @Transactional
    @PreAuthorize("hasAnyRole('ESTOQUE', 'GESTOR', 'GERENTE', 'DIRETORIA', 'ADMIN', 'SUPERUSER')")
    public ResponseEntity<SaldoResponse> ajustar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AjusteRequest request) {

        Long empresaId = user.getEmpresaId();
        if (request.quantidadeDelta().signum() == 0) {
            throw new BusinessException("Ajuste deve ser diferente de zero");
        }
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.depositoId(), empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
        produtoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(request.produtoId(), empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado"));

        SaldoEstoque saldo = repository.findForUpdate(empresaId, request.depositoId(), request.produtoId())
                .orElseGet(() -> {
                    SaldoEstoque novo = new SaldoEstoque();
                    novo.setEmpresaId(empresaId);
                    novo.setDepositoId(request.depositoId());
                    novo.setProdutoId(request.produtoId());
                    novo.setQuantidade(BigDecimal.ZERO);
                    novo.setAtualizadoEm(LocalDateTime.now());
                    return novo;
                });

        BigDecimal anterior = saldo.getQuantidade() == null ? BigDecimal.ZERO : saldo.getQuantidade();
        BigDecimal novo = anterior.add(request.quantidadeDelta());
        if (novo.signum() < 0) {
            throw new BusinessException("Ajuste deixaria o estoque negativo. Saldo atual: " + anterior);
        }

        saldo.setQuantidade(novo);
        saldo.setAtualizadoEm(LocalDateTime.now());
        SaldoEstoque salvo = repository.save(saldo);

        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setEmpresaId(empresaId);
        mov.setProdutoId(request.produtoId());
        mov.setDepositoId(request.depositoId());
        mov.setTipo(request.quantidadeDelta().signum() >= 0 ? "AJUSTE_ENTRADA" : "AJUSTE_SAIDA");
        mov.setOrigem("INVENTARIO_MANUAL");
        mov.setOrigemId(null);
        mov.setQuantidade(request.quantidadeDelta());
        mov.setSaldoApos(novo);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setObservacao(request.motivo());
        movimentacaoRepository.save(mov);

        return ResponseEntity.ok(SaldoResponse.from(salvo));
    }

    public record AjusteRequest(
            @NotNull Long depositoId,
            @NotNull Long produtoId,
            @NotNull BigDecimal quantidadeDelta,
            @NotNull String motivo) {}

    public record SaldoResponse(Long empresaId, Long produtoId, BigDecimal quantidade) {
        static SaldoResponse from(SaldoEstoque saldo) {
            return new SaldoResponse(saldo.getEmpresaId(), saldo.getProdutoId(), saldo.getQuantidade());
        }
    }
}
