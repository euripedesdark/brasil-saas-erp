package br.com.brasil_saas.estoque.controller;

import br.com.brasil_saas.estoque.model.*;
import br.com.brasil_saas.estoque.repository.*;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/estoque/inventarios")
@RequiredArgsConstructor
public class InventarioEstoqueController {
    private final InventarioEstoqueRepository inventarioRepository;
    private final InventarioEstoqueItemRepository itemRepository;
    private final DepositoRepository depositoRepository;
    private final EnderecoEstoqueRepository enderecoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final SaldoEstoqueRepository saldoRepository;
    private final ProdutoRepository produtoRepository;
    private final ReservaEstoqueRepository reservaRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('estoque:inventario:leitura')")
    public List<InventarioEstoque> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        return inventarioRepository.findByEmpresaIdAndDeletedAtIsNullOrderByDataContagemDesc(user.getEmpresaId());
    }

    @GetMapping("/{id}/itens")
    @PreAuthorize("hasAuthority('estoque:inventario:leitura')")
    public List<InventarioEstoqueItem> itens(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {
        inventarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id,user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Inventário não encontrado"));
        return itemRepository.findByInventarioIdOrderByIdAsc(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('estoque:inventario:escrita')")
    public ResponseEntity<InventarioEstoque> criar(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody CriarRequest request) {
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.depositoId(),user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Depósito inválido"));
        if (inventarioRepository.existsAberto(user.getEmpresaId(), request.depositoId()))
            throw new BusinessException("Já existe um inventário aberto para este depósito");
        InventarioEstoque i=new InventarioEstoque();
        i.setEmpresaId(user.getEmpresaId()); i.setDepositoId(request.depositoId()); i.setObservacoes(request.observacoes());
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioRepository.save(i));
    }

    @PostMapping("/{id}/contagens")
    @PreAuthorize("hasAuthority('estoque:inventario:escrita')")
    public ResponseEntity<InventarioEstoqueItem> contar(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody ContagemRequest request) {
        InventarioEstoque inv=inventarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id,user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Inventário não encontrado"));
        if(!"ABERTO".equals(inv.getStatus())) throw new BusinessException("Inventário não está aberto");
        if(request.quantidadeContada().signum()<0) throw new BusinessException("Quantidade contada não pode ser negativa");
        produtoRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(request.produtoId(), user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Produto não encontrado"));

        if(request.enderecoId()!=null) {
            EnderecoEstoque e=enderecoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.enderecoId(),user.getEmpresaId())
                    .orElseThrow(() -> new BusinessException("Endereço não encontrado"));
            if(!inv.getDepositoId().equals(e.getDepositoId())) throw new BusinessException("Endereço não pertence ao depósito do inventário");
        }
        if(itemRepository.existsSameScope(id, request.produtoId(), request.loteId(), request.enderecoId())) {
            throw new BusinessException("Já existe uma contagem para este produto/lote/endereço neste inventário");
        }
        if(itemRepository.existsMixedScope(id, request.produtoId(), request.loteId(), request.enderecoId())) {
            throw new BusinessException("Não é permitido misturar contagem por depósito com contagem por endereço para o mesmo produto/lote");
        }
        BigDecimal sistema;
        if(request.enderecoId()!=null) {
            sistema=movimentacaoRepository.saldosPorEndereco(user.getEmpresaId(),inv.getDepositoId(),request.produtoId(),request.loteId()).stream()
                    .filter(x->request.enderecoId().equals(x.getEnderecoId()))
                    .map(MovimentacaoEstoqueRepository.EnderecoSaldo::getQuantidade)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
        } else {
            sistema=saldoRepository.findByEmpresaIdAndDepositoIdAndProdutoId(user.getEmpresaId(),inv.getDepositoId(),request.produtoId())
                    .map(SaldoEstoque::getQuantidade).orElse(BigDecimal.ZERO);
        }
        InventarioEstoqueItem item=new InventarioEstoqueItem();
        item.setInventarioId(id); item.setProdutoId(request.produtoId()); item.setLoteId(request.loteId()); item.setEnderecoId(request.enderecoId());
        item.setQuantidadeSistema(sistema); item.setQuantidadeContada(request.quantidadeContada()); item.setDiferenca(request.quantidadeContada().subtract(sistema)); item.setObservacao(request.observacao());
        return ResponseEntity.status(HttpStatus.CREATED).body(itemRepository.save(item));
    }

    @PostMapping("/{id}/fechar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:inventario:ajuste')")
    public InventarioEstoque fechar(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {
        InventarioEstoque inv=inventarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id,user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Inventário não encontrado"));
        if(!"ABERTO".equals(inv.getStatus())) throw new BusinessException("Inventário já encerrado");
        List<InventarioEstoqueItem> itens=itemRepository.findByInventarioIdOrderByIdAsc(id);
        for(InventarioEstoqueItem item:itens) {
            if(item.getQuantidadeContada().signum()<0) throw new BusinessException("Contagem inválida");
            if(item.getDiferenca().signum()==0) continue;

            BigDecimal reservado = item.getEnderecoId() != null
                    ? reservaRepository.sumAtivasPorEndereco(user.getEmpresaId(), inv.getDepositoId(), item.getProdutoId(), item.getEnderecoId())
                    : reservaRepository.sumAtivas(user.getEmpresaId(), inv.getDepositoId(), item.getProdutoId());
            if (item.getQuantidadeContada().compareTo(reservado == null ? BigDecimal.ZERO : reservado) < 0) {
                throw new BusinessException("Contagem abaixo da quantidade reservada para o produto " + item.getProdutoId());
            }

            SaldoEstoque saldo=saldoRepository.findForUpdate(user.getEmpresaId(),inv.getDepositoId(),item.getProdutoId()).orElseGet(()->{
                SaldoEstoque s=new SaldoEstoque(); s.setEmpresaId(user.getEmpresaId()); s.setDepositoId(inv.getDepositoId()); s.setProdutoId(item.getProdutoId()); s.setQuantidade(BigDecimal.ZERO); return s;
            });
            saldo.setQuantidade(saldo.getQuantidade().add(item.getDiferenca()));
            saldo.setAtualizadoEm(LocalDateTime.now()); saldoRepository.save(saldo);
            MovimentacaoEstoque m=new MovimentacaoEstoque();
            m.setEmpresaId(user.getEmpresaId()); m.setProdutoId(item.getProdutoId()); m.setDepositoId(inv.getDepositoId());
            m.setEnderecoId(item.getEnderecoId()); m.setLoteId(item.getLoteId()); m.setTipo("AJUSTE_INVENTARIO"); m.setOrigem("INVENTARIO"); m.setOrigemId(inv.getId());
            m.setQuantidade(item.getDiferenca()); m.setSaldoApos(saldo.getQuantidade()); m.setDataMovimento(LocalDateTime.now());
            m.setObservacao("Ajuste de inventário. Sistema="+item.getQuantidadeSistema()+", contado="+item.getQuantidadeContada());
            movimentacaoRepository.save(m);
        }
        inv.setStatus("FECHADO"); inv.setUpdatedAt(LocalDateTime.now());
        return inventarioRepository.save(inv);
    }

    @PostMapping("/{id}/cancelar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:inventario:escrita')")
    public InventarioEstoque cancelar(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        InventarioEstoque inv = inventarioRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, user.getEmpresaId())
                .orElseThrow(() -> new BusinessException("Inventário não encontrado"));
        if (!"ABERTO".equals(inv.getStatus()))
            throw new BusinessException("Somente inventário ABERTO pode ser cancelado");
        inv.setStatus("CANCELADO");
        inv.setUpdatedAt(LocalDateTime.now());
        return inventarioRepository.save(inv);
    }

    public record CriarRequest(@NotNull Long depositoId,String observacoes) {}
    public record ContagemRequest(@NotNull Long produtoId,Long loteId,Long enderecoId,@NotNull @DecimalMin("0.000") BigDecimal quantidadeContada,String observacao) {}
}
