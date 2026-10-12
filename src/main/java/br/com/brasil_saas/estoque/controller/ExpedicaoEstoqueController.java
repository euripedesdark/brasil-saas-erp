package br.com.brasil_saas.estoque.controller;

import br.com.brasil_saas.estoque.model.*;
import br.com.brasil_saas.estoque.repository.*;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/estoque/expedicoes")
@RequiredArgsConstructor
public class ExpedicaoEstoqueController {
    private final ExpedicaoEstoqueRepository repository;
    private final ExpedicaoEstoqueItemRepository itemRepository;
    private final ReservaEstoqueRepository reservaRepository;
    private final DepositoRepository depositoRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('estoque:expedicao:leitura')")
    public List<ExpedicaoEstoque> listar(@AuthenticationPrincipal AuthenticatedUser user) {
        return repository.findByEmpresaIdAndDeletedAtIsNullOrderByDataAberturaDesc(user.getEmpresaId());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('estoque:expedicao:escrita')")
    public ExpedicaoEstoque abrir(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody AbrirRequest request) {
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.depositoId(), user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
        if (!repository.findByEmpresaIdAndPedidoVendaIdAndDeletedAtIsNull(user.getEmpresaId(), request.pedidoVendaId()).isEmpty())
            throw new BusinessException("Ja existe expedicao ativa para este pedido");

        List<ReservaEstoque> reservas = reservaRepository
                .findByEmpresaIdAndPedidoVendaIdAndDeletedAtIsNull(user.getEmpresaId(), request.pedidoVendaId())
                .stream().filter(r -> "RESERVADA".equals(r.getStatus())
                        || "SEPARACAO".equals(r.getStatus())
                        || "CONSUMIDA".equals(r.getStatus())).toList();

        if (reservas.isEmpty()) throw new BusinessException("O pedido nao possui reservas de estoque aptas para expedicao");

        ExpedicaoEstoque e = new ExpedicaoEstoque();
        e.setEmpresaId(user.getEmpresaId()); e.setPedidoVendaId(request.pedidoVendaId());
        e.setDepositoId(request.depositoId()); e.setStatus("ABERTA");
        e.setObservacoes(request.observacoes());
        e=repository.save(e);

        for(ReservaEstoque r: reservas){
            ExpedicaoEstoqueItem item=new ExpedicaoEstoqueItem();
            item.setEmpresaId(user.getEmpresaId()); item.setExpedicaoId(e.getId());
            item.setReservaId(r.getId()); item.setProdutoId(r.getProdutoId()); item.setLoteId(r.getLoteId()); item.setEnderecoId(r.getEnderecoId()); item.setQuantidade(r.getQuantidade());
            itemRepository.save(item);
        }
        return e;
    }

    @PostMapping("/{id}/separar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:expedicao:escrita')")
    public ExpedicaoEstoque separar(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){
        ExpedicaoEstoque e=get(user,id);
        if(!"ABERTA".equals(e.getStatus())) throw new BusinessException("Expedicao nao esta ABERTA");
        e.setStatus("SEPARACAO"); e.setDataSeparacao(LocalDateTime.now());
        for(ExpedicaoEstoqueItem i:itemRepository.findByEmpresaIdAndExpedicaoIdOrderByIdAsc(user.getEmpresaId(),id)){
            if(i.getReservaId()!=null) reservaRepository.findWithLockByIdAndEmpresaIdAndDeletedAtIsNull(i.getReservaId(), user.getEmpresaId()).ifPresent(r->{
                // Se o faturamento já consumiu a reserva, a expedição é apenas logística.
                if ("RESERVADA".equals(r.getStatus())) {
                    r.setStatus("SEPARACAO");
                    reservaRepository.save(r);
                }
            });
            i.setStatus("SEPARADO"); itemRepository.save(i);
        }
        return repository.save(e);
    }

    @PostMapping("/{id}/embalar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:expedicao:escrita')")
    public ExpedicaoEstoque embalar(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){
        ExpedicaoEstoque e=get(user,id);
        if(!"SEPARACAO".equals(e.getStatus())) throw new BusinessException("Expedicao precisa estar em SEPARACAO");
        e.setStatus("EMBALAGEM"); e.setDataEmbalagem(LocalDateTime.now());
        return repository.save(e);
    }

    @PostMapping("/{id}/expedir")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:expedicao:escrita')")
    public ExpedicaoEstoque expedir(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){
        ExpedicaoEstoque e=get(user,id);
        if(!"EMBALAGEM".equals(e.getStatus())) throw new BusinessException("Expedicao precisa estar em EMBALAGEM");
        var itens = itemRepository.findByEmpresaIdAndExpedicaoIdOrderByIdAsc(user.getEmpresaId(), id);
        if (itens.isEmpty()) throw new BusinessException("Expedicao sem itens");
        if (itens.stream().anyMatch(i -> !"SEPARADO".equals(i.getStatus()) && !"EMBALADO".equals(i.getStatus()))) throw new BusinessException("Todos os itens precisam estar separados antes da expedicao");
        e.setStatus("EXPEDIDA"); e.setDataExpedicao(LocalDateTime.now());
        for(ExpedicaoEstoqueItem i:itemRepository.findByEmpresaIdAndExpedicaoIdOrderByIdAsc(user.getEmpresaId(),id)){
            i.setStatus("EXPEDIDO"); itemRepository.save(i);
            if(i.getReservaId()!=null) reservaRepository.findById(i.getReservaId()).ifPresent(r->{
                if (!"CONSUMIDA".equals(r.getStatus())) {
                    r.setStatus("CONSUMIDA");
                    reservaRepository.save(r);
                }
            });
        }
        return repository.save(e);
    }

    private ExpedicaoEstoque get(AuthenticatedUser user,Long id){
        return repository.findByIdAndEmpresaIdAndDeletedAtIsNull(id,user.getEmpresaId())
                .orElseThrow(()->new ResourceNotFoundException("Expedicao nao encontrada"));
    }

    public record AbrirRequest(@NotNull Long pedidoVendaId,@NotNull Long depositoId,String observacoes){}
}