package br.com.brasil_saas.estoque.controller;

import br.com.brasil_saas.estoque.model.ReservaEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.model.LoteEstoque;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import br.com.brasil_saas.estoque.repository.LoteEstoqueRepository;
import br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.EnderecoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
@RequestMapping("/api/estoque/reservas")
@RequiredArgsConstructor
public class ReservaEstoqueController {
    private final ReservaEstoqueRepository repository;
    private final PedidoVendaRepository pedidoRepository;
    private final SaldoEstoqueRepository saldoRepository;
    private final DepositoRepository depositoRepository;
    private final LoteEstoqueRepository loteRepository;
    private final EnderecoEstoqueRepository enderecoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('estoque:reserva:leitura')")
    public List<ReservaEstoque> listar(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(required = false) Long pedidoVendaId) {
        if (pedidoVendaId != null)
            return repository.findByEmpresaIdAndPedidoVendaIdAndDeletedAtIsNull(user.getEmpresaId(), pedidoVendaId);
        return repository.findByEmpresaIdAndDeletedAtIsNullOrderByDataReservaDesc(user.getEmpresaId());
    }

    /**
     * Sugere de quais lotes reservar/separar a quantidade pedida, por FEFO (padrao)
     * ou FIFO. Somente leitura: o chamador cria uma reserva por lote sugerido
     * (POST /api/estoque/reservas com loteId), que continua travando e validando.
     */
    @GetMapping("/sugerir-lotes")
    @PreAuthorize("hasAuthority('estoque:reserva:leitura')")
    public Map<String, Object> sugerirLotes(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam Long depositoId,
                                            @RequestParam Long produtoId,
                                            @RequestParam BigDecimal quantidade,
                                            @RequestParam(defaultValue = "FEFO") String estrategia) {
        if (quantidade == null || quantidade.signum() <= 0)
            throw new BusinessException("Quantidade deve ser maior que zero");
        br.com.brasil_saas.estoque.service.LoteSelector.Estrategia est;
        try { est = br.com.brasil_saas.estoque.service.LoteSelector.Estrategia.valueOf(estrategia.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new BusinessException("Estrategia deve ser FEFO ou FIFO"); }

        List<br.com.brasil_saas.estoque.service.LoteSelector.LoteDisponivel> lotes = new java.util.ArrayList<>();
        for (LoteEstoque l : loteRepository.findByEmpresaIdAndProdutoIdAndDeletedAtIsNullOrderByDataValidadeAsc(user.getEmpresaId(), produtoId)) {
            if (l.getDepositoId() != null && !l.getDepositoId().equals(depositoId)) continue;
            BigDecimal reservado = repository.sumAtivasPorLote(user.getEmpresaId(), depositoId, produtoId, l.getId());
            BigDecimal livre = (l.getQuantidade() == null ? BigDecimal.ZERO : l.getQuantidade())
                    .subtract(reservado == null ? BigDecimal.ZERO : reservado);
            lotes.add(new br.com.brasil_saas.estoque.service.LoteSelector.LoteDisponivel(
                    l.getId(), l.getStatus(), l.getDataValidade(), l.getDataFabricacao(), livre));
        }
        var r = br.com.brasil_saas.estoque.service.LoteSelector.selecionar(lotes, quantidade, est, java.time.LocalDate.now());
        return Map.of("estrategia", est.name(), "completo", r.completo(),
                "faltante", r.faltante(), "alocacoes", r.alocacoes());
    }

    @GetMapping("/picking-sugestoes")
    @PreAuthorize("hasAuthority('estoque:picking:leitura')")
    public List<Map<String,Object>> pickingSugestoes(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @RequestParam Long depositoId,
                                                      @RequestParam Long produtoId,
                                                      @RequestParam(required = false) Long loteId) {
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(depositoId, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
        return movimentacaoRepository.saldosPorEndereco(user.getEmpresaId(), depositoId, produtoId, loteId)
                .stream().map(x -> {
                    Map<String,Object> item = new java.util.LinkedHashMap<>();
                    item.put("enderecoId", x.getEnderecoId());
                    item.put("loteId", x.getLoteId());
                    item.put("quantidade", x.getQuantidade());
                    item.put("validade", x.getValidade() == null ? null : x.getValidade().toString());
                    return item;
                }).toList();
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAuthority('estoque:reserva:escrita')")
    public ReservaEstoque reservar(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody Request request) {
        if (request.quantidade() == null || request.quantidade().signum() <= 0)
            throw new BusinessException("Quantidade deve ser maior que zero");
        if (request.dataExpiracao() != null && !request.dataExpiracao().isAfter(LocalDateTime.now()))
            throw new BusinessException("Data de expiracao deve ser futura");
        var pedido = request.pedidoVendaId() == null ? null : pedidoRepository
                .findByIdForUpdateAndEmpresaId(request.pedidoVendaId(), user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado nesta empresa"));
        if (pedido != null && (pedido.getDeletedAt() != null || !"ABERTO".equals(pedido.getStatus()) || !"PEDIDO".equals(pedido.getTipo())))
            throw new BusinessException("Somente pedido ABERTO pode receber reserva");
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.depositoId(), user.getEmpresaId())
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Deposito nao encontrado"));
        SaldoEstoque saldo = saldoRepository.findForUpdate(user.getEmpresaId(), request.depositoId(), request.produtoId())
                .orElseThrow(() -> new BusinessException("Nao existe saldo do produto no deposito"));
        var reservasPedido = pedido == null ? List.<ReservaEstoque>of() : repository
                .findByEmpresaIdAndPedidoVendaIdAndDeletedAtIsNull(user.getEmpresaId(), pedido.getId());
        var mesmas = reservasPedido.stream()
                .filter(r -> List.of("RESERVADA", "SEPARACAO").contains(r.getStatus()))
                .filter(r -> request.depositoId().equals(r.getDepositoId()) && request.produtoId().equals(r.getProdutoId())
                        && java.util.Objects.equals(request.loteId(), r.getLoteId())
                        && java.util.Objects.equals(request.enderecoId(), r.getEnderecoId())).toList();
        if (mesmas.size() > 1) throw new BusinessException("Existem varias reservas na mesma posicao; ajuste cada reserva antes de substituir a quantidade");
        ReservaEstoque reserva = mesmas.isEmpty() ? new ReservaEstoque() : repository
                .findWithLockByIdAndEmpresaIdAndDeletedAtIsNull(mesmas.get(0).getId(), user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva nao encontrada"));
        if (reserva.getId() != null && !"RESERVADA".equals(reserva.getStatus()))
            throw new BusinessException("Somente reserva RESERVADA pode ter quantidade alterada");
        BigDecimal propria = reserva.getId() == null ? BigDecimal.ZERO : reserva.getQuantidade();
        if (pedido != null) {
            BigDecimal pedida = pedido.getItens().stream().filter(i -> request.produtoId().equals(i.getProdutoId()))
                    .map(i -> i.getQuantidade()).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outrasDoPedido = reservasPedido.stream()
                    .filter(r -> !java.util.Objects.equals(reserva.getId(), r.getId()))
                    .filter(r -> request.produtoId().equals(r.getProdutoId())
                            && List.of("RESERVADA", "SEPARACAO", "CONSUMIDA").contains(r.getStatus()))
                    .map(ReservaEstoque::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (outrasDoPedido.add(request.quantidade()).compareTo(pedida) > 0)
                throw new BusinessException("Reservas excedem a quantidade do produto no pedido");
        }
        BigDecimal reservado = repository.sumAtivas(user.getEmpresaId(), request.depositoId(), request.produtoId());
        BigDecimal disponivel = saldo.getQuantidade().subtract(zero(reservado)).add(propria);
        if (disponivel.compareTo(request.quantidade()) < 0)
            throw new BusinessException("Estoque disponivel insuficiente. Disponivel: " + disponivel);
        if (request.enderecoId() != null) {
            var endereco = enderecoRepository.findByIdAndEmpresaIdAndAtivoTrue(request.enderecoId(), user.getEmpresaId())
                    .filter(e -> e.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Endereco nao encontrado"));
            if (!request.depositoId().equals(endereco.getDepositoId()))
                throw new BusinessException("Endereco nao pertence ao deposito informado");
            var posicoes = movimentacaoRepository.saldosPorEndereco(user.getEmpresaId(), request.depositoId(), request.produtoId(), null);
            BigDecimal saldoEndereco = posicoes.stream().filter(x -> request.enderecoId().equals(x.getEnderecoId()))
                    .map(MovimentacaoEstoqueRepository.EnderecoSaldo::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal reservadoEndereco = repository.sumAtivasPorEndereco(user.getEmpresaId(), request.depositoId(), request.produtoId(), request.enderecoId());
            if (saldoEndereco.subtract(zero(reservadoEndereco)).add(propria).compareTo(request.quantidade()) < 0)
                throw new BusinessException("Quantidade insuficiente no endereco");
            if (request.loteId() != null) {
                BigDecimal saldoPosicao = posicoes.stream()
                        .filter(x -> request.enderecoId().equals(x.getEnderecoId()) && request.loteId().equals(x.getLoteId()))
                        .map(MovimentacaoEstoqueRepository.EnderecoSaldo::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal reservadoPosicao = repository.sumAtivasPorEnderecoELote(user.getEmpresaId(), request.depositoId(), request.produtoId(), request.enderecoId(), request.loteId());
                if (saldoPosicao.subtract(zero(reservadoPosicao)).add(propria).compareTo(request.quantidade()) < 0)
                    throw new BusinessException("Quantidade insuficiente no lote/endereco");
            }
        }
        if (request.loteId() != null) {
            LoteEstoque lote = loteRepository.findForUpdate(user.getEmpresaId(), request.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote nao encontrado"));
            if (!request.produtoId().equals(lote.getProdutoId()) || !request.depositoId().equals(lote.getDepositoId()))
                throw new BusinessException("Lote nao pertence ao produto/deposito informado");
            if (!"ATIVO".equals(lote.getStatus())) throw new BusinessException("Lote nao esta ATIVO");
            if (lote.getDataValidade() != null && lote.getDataValidade().isBefore(java.time.LocalDate.now()))
                throw new BusinessException("Lote vencido em " + lote.getDataValidade() + "; vencidos nao reservam");
            BigDecimal reservadoLote = repository.sumAtivasPorLote(user.getEmpresaId(), request.depositoId(), request.produtoId(), request.loteId());
            if (lote.getQuantidade().subtract(zero(reservadoLote)).add(propria).compareTo(request.quantidade()) < 0)
                throw new BusinessException("Quantidade insuficiente no lote");
        }

        reserva.setEmpresaId(user.getEmpresaId());
        reserva.setDepositoId(request.depositoId());
        reserva.setProdutoId(request.produtoId());
        reserva.setPedidoVendaId(request.pedidoVendaId());
        reserva.setLoteId(request.loteId());
        reserva.setEnderecoId(request.enderecoId());
        reserva.setQuantidade(request.quantidade());
        reserva.setStatus("RESERVADA");
        if (reserva.getDataReserva() == null) reserva.setDataReserva(LocalDateTime.now());
        reserva.setDataExpiracao(request.dataExpiracao());
        reserva.setDeletedAt(null);
        return repository.save(reserva);
    }

    @PostMapping("/{id}/liberar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:reserva:escrita')")
    public ReservaEstoque liberar(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        ReservaEstoque reserva = repository.findWithLockByIdAndEmpresaIdAndDeletedAtIsNull(id, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva nao encontrada"));
        if ("LIBERADA".equals(reserva.getStatus()) || "CANCELADA".equals(reserva.getStatus()) || "CONSUMIDA".equals(reserva.getStatus()))
            throw new BusinessException("Reserva ja esta encerrada");
        reserva.setStatus("LIBERADA");
        return repository.save(reserva);
    }

    @PostMapping("/{id}/separar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:reserva:escrita')")
    public ReservaEstoque separar(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        ReservaEstoque reserva = repository.findWithLockByIdAndEmpresaIdAndDeletedAtIsNull(id, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva nao encontrada"));
        if (!"RESERVADA".equals(reserva.getStatus()))
            throw new BusinessException("Somente reservas RESERVADAS podem ir para separacao");
        reserva.setStatus("SEPARACAO");
        return repository.save(reserva);
    }

    @PostMapping("/{id}/cancelar")
    @Transactional
    @PreAuthorize("hasAuthority('estoque:reserva:escrita')")
    public ReservaEstoque cancelar(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        ReservaEstoque reserva = repository.findWithLockByIdAndEmpresaIdAndDeletedAtIsNull(id, user.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Reserva nao encontrada"));
        if ("LIBERADA".equals(reserva.getStatus()) || "CANCELADA".equals(reserva.getStatus())
                || "CONSUMIDA".equals(reserva.getStatus())) {
            throw new BusinessException("Reserva ja esta encerrada e nao pode ser cancelada");
        }
        if ("SEPARACAO".equals(reserva.getStatus())) {
            throw new BusinessException("Reserva em SEPARACAO: libere ou conclua a expedicao antes de cancelar");
        }
        reserva.setStatus("CANCELADA");
        return repository.save(reserva);
    }

    private BigDecimal zero(BigDecimal valor) { return valor == null ? BigDecimal.ZERO : valor; }

    public record Request(@NotNull Long depositoId, @NotNull Long produtoId, Long pedidoVendaId, Long loteId,
                          Long enderecoId, @NotNull @DecimalMin("0.001") BigDecimal quantidade, LocalDateTime dataExpiracao) {}
}
