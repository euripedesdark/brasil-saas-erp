package br.com.brasil_saas.estoque.controller;

import br.com.brasil_saas.estoque.model.Deposito;
import br.com.brasil_saas.estoque.model.EnderecoEstoque;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.EnderecoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estoque/enderecos")
@RequiredArgsConstructor
public class EnderecoEstoqueController {
    private final EnderecoEstoqueRepository repository;
    private final DepositoRepository depositoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('estoque:endereco:leitura')")
    public List<EnderecoEstoque> listar(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestParam Long depositoId) {
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(depositoId, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
        return repository.findByEmpresaIdAndDepositoIdAndAtivoTrueOrderByCodigoAsc(user.getEmpresaId(), depositoId);
    }

    @GetMapping("/ocupacao")
    @PreAuthorize("hasAuthority('estoque:picking:leitura')")
    public List<Map<String,Object>> ocupacao(@AuthenticationPrincipal AuthenticatedUser user,
                                               @RequestParam Long depositoId,
                                               @RequestParam(required = false) Long produtoId,
                                               @RequestParam(required = false) Long loteId) {
        validarDeposito(user, depositoId);
        return movimentacaoRepository.saldosPorEnderecoCompleto(user.getEmpresaId(), depositoId, produtoId, loteId)
                .stream().map(x -> {
                    Map<String,Object> item = new java.util.LinkedHashMap<>();
                    item.put("enderecoId", x.getEnderecoId());
                    item.put("produtoId", x.getProdutoId());
                    item.put("loteId", x.getLoteId());
                    item.put("quantidade", x.getQuantidade());
                    return item;
                }).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('estoque:endereco:escrita')")
    public EnderecoEstoque criar(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody Request request) {
        validarDeposito(user, request.depositoId());
        repository.findByEmpresaIdAndDepositoIdAndCodigoAndAtivoTrue(user.getEmpresaId(), request.depositoId(), request.codigo())
                .ifPresent(x -> { throw new BusinessException("Ja existe endereco com este codigo no deposito"); });

        EnderecoEstoque e = new EnderecoEstoque();
        e.setEmpresaId(user.getEmpresaId());
        e.setDepositoId(request.depositoId());
        e.setCodigo(request.codigo().trim().toUpperCase());
        e.setDescricao(request.descricao());
        e.setTipo(request.tipo() == null || request.tipo().isBlank() ? "PULMAO" : request.tipo());
        e.setCapacidade(request.capacidade());
        e.setAtivo(true);
        return repository.save(e);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:endereco:escrita')")
    public EnderecoEstoque atualizar(@AuthenticationPrincipal AuthenticatedUser user,
                                     @PathVariable Long id, @Valid @RequestBody Request request) {
        EnderecoEstoque e = repository.findByIdAndEmpresaIdAndAtivoTrue(id, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Endereco nao encontrado"));
        validarDeposito(user, request.depositoId());
        repository.findByEmpresaIdAndDepositoIdAndCodigoAndAtivoTrue(user.getEmpresaId(), request.depositoId(), request.codigo())
                .filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw new BusinessException("Ja existe endereco com este codigo no deposito"); });

        e.setDepositoId(request.depositoId());
        e.setCodigo(request.codigo().trim().toUpperCase());
        e.setDescricao(request.descricao());
        e.setTipo(request.tipo() == null || request.tipo().isBlank() ? "PULMAO" : request.tipo());
        e.setCapacidade(request.capacidade());
        return repository.save(e);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:endereco:escrita')")
    public void excluir(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        EnderecoEstoque e = repository.findByIdAndEmpresaIdAndAtivoTrue(id, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Endereco nao encontrado"));
        e.setAtivo(false);
        e.setDeletedAt(LocalDateTime.now());
        repository.save(e);
    }

    private void validarDeposito(AuthenticatedUser user, Long depositoId) {
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(depositoId, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
    }

    public record Request(@NotNull Long depositoId, @NotBlank String codigo, String descricao,
                          String tipo, BigDecimal capacidade) {}
}