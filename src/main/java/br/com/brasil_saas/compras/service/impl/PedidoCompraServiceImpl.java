package br.com.brasil_saas.compras.service.impl;

import br.com.brasil_saas.compras.dto.PedidoCompraRequest;
import br.com.brasil_saas.compras.dto.PedidoCompraResponse;
import br.com.brasil_saas.compras.model.ItemPedidoCompra;
import br.com.brasil_saas.compras.model.PedidoCompra;
import br.com.brasil_saas.compras.model.RecebimentoCompra;
import br.com.brasil_saas.compras.model.RecebimentoCompraItem;
import br.com.brasil_saas.compras.repository.PedidoCompraRepository;
import br.com.brasil_saas.cadastro.repository.FornecedorRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraItemRepository;
import br.com.brasil_saas.compras.repository.RecebimentoCompraRepository;
import br.com.brasil_saas.compras.repository.AlcadaAprovacaoRepository;
import br.com.brasil_saas.compras.service.PedidoCompraService;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.qualidade.model.Inspecao;
import br.com.brasil_saas.qualidade.model.PlanoInspecao;
import br.com.brasil_saas.qualidade.repository.InspecaoRepository;
import br.com.brasil_saas.qualidade.repository.PlanoInspecaoRepository;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PedidoCompraServiceImpl implements PedidoCompraService {

    private final PedidoCompraRepository pedidoRepository;
    private final FornecedorRepository fornecedorRepository;
    private final RecebimentoCompraRepository recebimentoRepository;
    private final RecebimentoCompraItemRepository recebimentoItemRepository;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final DepositoRepository depositoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final TituloRepository tituloRepository;
    private final AlcadaAprovacaoRepository alcadaRepository;
    private final br.com.brasil_saas.financeiro.service.TituloService tituloService;
    private final DocumentoFluxoService documentoFluxoService;
    private final PlanoInspecaoRepository planoInspecaoRepository;
    private final InspecaoRepository inspecaoRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private br.com.brasil_saas.contabilidade.service.EventoContabilService eventoContabil;

    @Override
    @Transactional
    public PedidoCompraResponse criar(PedidoCompraRequest request) {
        PedidoCompra pedido = new PedidoCompra();
        pedido.setEmpresaId(request.empresaId());
        if (request.itens() == null || request.itens().isEmpty()) throw new BusinessException("O pedido de compra precisa possuir ao menos um item");
        if (!fornecedorRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(request.fornecedorId(), request.empresaId()).isPresent()) throw new ResourceNotFoundException("Fornecedor nao encontrado para a empresa");
        pedido.setFornecedorId(request.fornecedorId());
        String numero = request.numero();
        if (numero == null || numero.isBlank()) numero = "PC-" + System.currentTimeMillis();
        pedido.setNumero(numero);
        pedido.setStatus(request.status() != null ? request.status() : "ABERTO");
        pedido.setDataEmissao(request.dataEmissao() != null ? request.dataEmissao() : LocalDate.now());
        pedido.setDataPrevisaoEntrega(request.dataPrevisaoEntrega());
        pedido.setCondicaoPagamentoId(request.condicaoPagamentoId());
        pedido.setObservacao(request.observacao());

        List<ItemPedidoCompra> itens = new ArrayList<>();
        BigDecimal totalProdutos = BigDecimal.ZERO;
        if (request.itens() != null) {
            int num = 1;
            for (var itemReq : request.itens()) {
                ItemPedidoCompra item = new ItemPedidoCompra();
                item.setPedido(pedido);
                item.setEmpresaId(pedido.getEmpresaId());
                item.setNumeroItem(itemReq.numeroItem() != null ? itemReq.numeroItem() : num++);
                item.setProdutoId(itemReq.produtoId());
                item.setDescricao(itemReq.descricao());
                if (itemReq.quantidade() == null || itemReq.quantidade().signum() <= 0) throw new BusinessException("Quantidade deve ser maior que zero");
                if (itemReq.valorUnitario() == null || itemReq.valorUnitario().signum() < 0) throw new BusinessException("Valor unitario invalido");
                item.setQuantidade(itemReq.quantidade());
                item.setUnidade(itemReq.unidade());
                item.setValorUnitario(itemReq.valorUnitario());
                item.setValorDesconto(itemReq.valorDesconto() != null ? itemReq.valorDesconto() : BigDecimal.ZERO);
                BigDecimal valorTotal = itemReq.quantidade().multiply(itemReq.valorUnitario()).subtract(item.getValorDesconto());
                item.setValorTotal(valorTotal);
                item.setCriadoEstoque(false);
                itens.add(item);
                totalProdutos = totalProdutos.add(valorTotal);
            }
        }

        pedido.setItens(itens);
        pedido.setValorProdutos(totalProdutos);
        pedido.setValorDesconto(request.valorDesconto() != null ? request.valorDesconto() : BigDecimal.ZERO);
        pedido.setValorFrete(request.valorFrete() != null ? request.valorFrete() : BigDecimal.ZERO);
        pedido.setValorTotal(totalProdutos.subtract(pedido.getValorDesconto()).add(pedido.getValorFrete()));
        pedido.setStatusAprovacao(exigeAprovacao(pedido.getEmpresaId(), pedido.getValorTotal()) ? "PENDENTE" : "APROVADO");
        return PedidoCompraResponse.from(pedidoRepository.save(pedido));
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoCompraResponse buscarPorId(Long id, Long empresaId) {
        PedidoCompra pedido = pedidoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        return PedidoCompraResponse.from(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoCompraResponse> listarPorEmpresa(Long empresaId) {
        return pedidoRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId).stream()
                .map(PedidoCompraResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void receber(Long id, Long empresaId) {
        PedidoCompra pedido = pedidoRepository.findByIdForUpdateAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if (!"ABERTO".equals(pedido.getStatus())) {
            throw new BusinessException("Apenas pedidos ABERTOS podem ser recebidos");
        }
        exigirAprovado(pedido);

        Map<Long, BigDecimal> recebidas = new java.util.HashMap<>();
        for (ItemPedidoCompra item : pedido.getItens()) {
            if (item.getProdutoId() != null && !Boolean.TRUE.equals(item.getCriadoEstoque())) {
                entrarEstoque(pedido.getEmpresaId(), item.getProdutoId(), item.getQuantidade(), pedido.getId());
                item.setQuantidadeRecebida(item.getQuantidade());
                item.setCriadoEstoque(true);
                recebidas.put(item.getId(), item.getQuantidade());
            }
        }

        RecebimentoCompra recebimento = registrarRecebimento(pedido, recebidas);
        gerarInspecaoRecebimento(pedido.getEmpresaId(), recebimento);
        Titulo titulo = criarTituloCompra(pedido);
        pedido.setTituloId(titulo.getId());
        tituloService.gerarParcelas(pedido.getEmpresaId(), titulo.getId(), pedido.getCondicaoPagamentoId());
        pedido.setStatus("RECEBIDO");
        pedidoRepository.save(pedido);
        documentoFluxoService.ligar(empresaId, null,
                "PEDIDO_COMPRA", pedido.getId(), pedido.getNumero(),
                "RECEBIMENTO", pedido.getId(), pedido.getNumero(),
                "GERA");
        documentoFluxoService.ligar(empresaId, null,
                "PEDIDO_COMPRA", pedido.getId(), pedido.getNumero(),
                "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                "FATURA");
    }

    @Override
    @Transactional
    public void receberParcial(Long id, Long empresaId, Map<Long, BigDecimal> quantidades) {
        PedidoCompra pedido = pedidoRepository.findByIdForUpdateAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if ("CANCELADO".equals(pedido.getStatus()) || "RECEBIDO".equals(pedido.getStatus())) {
            throw new BusinessException("Pedido nao pode receber novas quantidades no status " + pedido.getStatus());
        }
        exigirAprovado(pedido);
        if (quantidades == null || quantidades.isEmpty()) {
            throw new BusinessException("Informe ao menos uma quantidade de recebimento");
        }

        boolean recebeuAlgo = false;
        boolean tudoRecebido = true;
        Map<Long, BigDecimal> recebidas = new java.util.HashMap<>();

        for (ItemPedidoCompra item : pedido.getItens()) {
            BigDecimal entrada = quantidades.get(item.getId());
            BigDecimal jaRecebida = item.getQuantidadeRecebida() == null ? BigDecimal.ZERO : item.getQuantidadeRecebida();
            BigDecimal restante = item.getQuantidade().subtract(jaRecebida);

            if (entrada == null) {
                if (restante.signum() > 0) tudoRecebido = false;
                continue;
            }
            if (entrada.signum() < 0 || entrada.compareTo(restante) > 0) {
                throw new BusinessException("Quantidade recebida invalida para o item " + item.getNumeroItem()
                        + ". Restante: " + restante + ", informado: " + entrada);
            }
            if (entrada.signum() > 0) {
                if (item.getProdutoId() != null) {
                    entrarEstoque(pedido.getEmpresaId(), item.getProdutoId(), entrada, pedido.getId());
                }
                item.setQuantidadeRecebida(jaRecebida.add(entrada));
                item.setCriadoEstoque(item.getQuantidadeRecebida().compareTo(item.getQuantidade()) >= 0);
                recebidas.put(item.getId(), entrada);
                recebeuAlgo = true;
            }
            if (item.getQuantidadeRecebida().compareTo(item.getQuantidade()) < 0) tudoRecebido = false;
        }

        if (!recebeuAlgo) throw new BusinessException("Nenhuma quantidade nova foi recebida");
        RecebimentoCompra recebimentoParcial = registrarRecebimento(pedido, recebidas);
        gerarInspecaoRecebimento(pedido.getEmpresaId(), recebimentoParcial);

        if (tudoRecebido) {
            pedido.setStatus("RECEBIDO");
            if (pedido.getTituloId() == null) {
                Titulo titulo = criarTituloCompra(pedido);
                pedido.setTituloId(titulo.getId());
                tituloService.gerarParcelas(pedido.getEmpresaId(), titulo.getId(), pedido.getCondicaoPagamentoId());
            }
        } else {
            pedido.setStatus("PARCIAL");
        }
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public void cancelar(Long id, Long empresaId) {
        PedidoCompra pedido = pedidoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if ("RECEBIDO".equals(pedido.getStatus())) {
            throw new BusinessException("Pedidos RECEBIDOS nao podem ser cancelados diretamente");
        }
        pedido.setStatus("CANCELADO");
        pedidoRepository.save(pedido);
    }

    private boolean exigeAprovacao(Long empresaId, BigDecimal total) {
        if (total == null) return false;
        return alcadaRepository.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByValorLimiteAsc(empresaId)
                .stream().findFirst()
                .map(a -> total.compareTo(a.getValorLimite()) > 0)
                .orElse(false);
    }

    private void exigirAprovado(PedidoCompra pedido) {
        if ("REJEITADO".equals(pedido.getStatusAprovacao()))
            throw new BusinessException("Pedido rejeitado na aprovacao nao pode ser recebido");
        if ("PENDENTE".equals(pedido.getStatusAprovacao())
                && exigeAprovacao(pedido.getEmpresaId(), pedido.getValorTotal()))
            throw new BusinessException("Pedido acima da alcada aguarda aprovacao");
    }

    @Override
    @Transactional
    public PedidoCompraResponse aprovar(Long id, Long empresaId, Long userId) {
        PedidoCompra pedido = pedidoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if (!"PENDENTE".equals(pedido.getStatusAprovacao()))
            throw new BusinessException("Somente pedidos pendentes podem ser aprovados");
        pedido.setStatusAprovacao("APROVADO");
        pedido.setAprovadoPor(userId);
        pedido.setAprovadoEm(LocalDateTime.now());
        pedido.setMotivoRejeicao(null);
        return PedidoCompraResponse.from(pedidoRepository.save(pedido));
    }

    @Override
    @Transactional
    public PedidoCompraResponse rejeitar(Long id, Long empresaId, Long userId, String motivo) {
        PedidoCompra pedido = pedidoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if (!"PENDENTE".equals(pedido.getStatusAprovacao()))
            throw new BusinessException("Somente pedidos pendentes podem ser rejeitados");
        if (motivo == null || motivo.trim().length() < 10)
            throw new BusinessException("Motivo da rejeicao com ao menos 10 caracteres");
        pedido.setStatusAprovacao("REJEITADO");
        pedido.setAprovadoPor(userId);
        pedido.setAprovadoEm(LocalDateTime.now());
        pedido.setMotivoRejeicao(motivo.trim());
        return PedidoCompraResponse.from(pedidoRepository.save(pedido));
    }

    private Titulo criarTituloCompra(PedidoCompra pedido) {
        Titulo titulo = new Titulo();
        titulo.setEmpresaId(pedido.getEmpresaId());
        titulo.setTipo("P");
        titulo.setNumeroDocumento(pedido.getNumero());
        titulo.setDescricao("Compra - Pedido " + pedido.getNumero());
        Long pessoaId = fornecedorRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(pedido.getFornecedorId(), pedido.getEmpresaId())
                .map(f -> f.getPessoa() == null ? null : f.getPessoa().getId())
                .orElseThrow(() -> new BusinessException("Fornecedor do pedido nao possui pessoa vinculada"));
        titulo.setPessoaId(pessoaId);
        titulo.setValorOriginal(pedido.getValorTotal());
        titulo.setValorSaldo(pedido.getValorTotal());
        titulo.setDataEmissao(LocalDate.now());
        titulo.setDataVencimento(LocalDate.now().plusDays(30));
        titulo.setStatus("ABERTO");
        return tituloRepository.save(titulo);
    }

    private RecebimentoCompra registrarRecebimento(PedidoCompra pedido, Map<Long, BigDecimal> recebidas) {
        if (recebidas.isEmpty()) return null;

        RecebimentoCompra recebimento = new RecebimentoCompra();
        recebimento.setEmpresaId(pedido.getEmpresaId());
        recebimento.setPedidoId(pedido.getId());
        recebimento.setNumero("REC-" + System.currentTimeMillis());
        recebimento.setDataRecebimento(LocalDate.now());
        recebimento.setStatus("RECEBIDO");

        BigDecimal valorTotal = BigDecimal.ZERO;
        List<RecebimentoCompraItem> itens = new ArrayList<>();
        for (ItemPedidoCompra item : pedido.getItens()) {
            BigDecimal quantidade = recebidas.get(item.getId());
            if (quantidade == null || quantidade.signum() <= 0 || item.getProdutoId() == null) continue;

            RecebimentoCompraItem recebido = new RecebimentoCompraItem();
            recebido.setEmpresaId(pedido.getEmpresaId());
            recebido.setProdutoId(item.getProdutoId());
            recebido.setQuantidadePedida(item.getQuantidade());
            recebido.setQuantidadeRecebida(quantidade);
            recebido.setValorUnitario(item.getValorUnitario());
            itens.add(recebido);
            valorTotal = valorTotal.add(quantidade.multiply(item.getValorUnitario()));
        }

        recebimento.setValorTotal(valorTotal);
        RecebimentoCompra salvo = recebimentoRepository.save(recebimento);
        for (RecebimentoCompraItem item : itens) item.setRecebimentoId(salvo.getId());
        recebimentoItemRepository.saveAll(itens);
        contabilizarRecebimento(pedido, salvo, valorTotal);
        return salvo;
    }

    /** Reconhece a compra na contabilidade no recebimento (evento RECEBIMENTO_COMPRA). Sem regra, fica como pendencia e nao bloqueia o recebimento. */
    private void contabilizarRecebimento(PedidoCompra pedido, RecebimentoCompra recebimento, BigDecimal valor) {
        if (eventoContabil == null) return;
        try {
            eventoContabil.contabilizar(pedido.getEmpresaId(),
                    br.com.brasil_saas.contabilidade.service.EventoContabilService.EVT_RECEBIMENTO_COMPRA,
                    "RECEBIMENTO", recebimento.getId(), recebimento.getDataRecebimento(), valor,
                    "Recebimento " + recebimento.getNumero() + " pedido " + pedido.getNumero());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 422) throw ex;
        }
    }

    /** Gera inspeção automática se existir plano ativo de tipo RECEBIMENTO (integração QM ↔ Compras). */
    private void gerarInspecaoRecebimento(Long empresaId, RecebimentoCompra recebimento) {
        if (recebimento == null) return;
        List<PlanoInspecao> planos = planoInspecaoRepository.findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresaId);
        PlanoInspecao plano = planos.stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && "RECEBIMENTO".equalsIgnoreCase(p.getTipo()))
                .findFirst()
                .orElse(null);
        if (plano == null) return;

        Inspecao insp = new Inspecao();
        insp.setEmpresaId(empresaId);
        insp.setPlanoId(plano.getId());
        insp.setReferenciaTipo("RECEBIMENTO");
        insp.setReferenciaId(recebimento.getId());
        insp.setNumero("INSP-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        insp.setDataInspecao(LocalDate.now());
        insp.setStatus("ABERTA");
        insp.setObservacao("Gerada automaticamente no recebimento " + recebimento.getNumero());
        inspecaoRepository.save(insp);
    }

    private void entrarEstoque(Long empresaId, Long produtoId, BigDecimal quantidade, Long origemId) {
        SaldoEstoque saldo = saldoEstoqueRepository.findByEmpresaIdAndProdutoIdForUpdate(empresaId, produtoId)
                .orElseGet(() -> {
                    SaldoEstoque novo = new SaldoEstoque();
                    novo.setEmpresaId(empresaId);
                    novo.setDepositoId(depositoRepository.findByEmpresaIdAndCodigoAndAtivoTrue(empresaId, "PADRAO")
                            .orElseThrow(() -> new BusinessException("Deposito PADRAO nao encontrado para a empresa " + empresaId))
                            .getId());
                    novo.setProdutoId(produtoId);
                    novo.setQuantidade(BigDecimal.ZERO);
                    return novo;
                });

        BigDecimal saldoAtual = saldo.getQuantidade() != null ? saldo.getQuantidade() : BigDecimal.ZERO;
        saldo.setQuantidade(saldoAtual.add(quantidade));
        saldoEstoqueRepository.save(saldo);

        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setEmpresaId(empresaId);
        mov.setProdutoId(produtoId);
        mov.setTipo("ENTRADA");
        mov.setOrigem("COMPRA");
        mov.setOrigemId(origemId);
        mov.setQuantidade(quantidade);
        mov.setSaldoApos(saldo.getQuantidade());
        mov.setObservacao("Entrada por compra");
        movimentacaoRepository.save(mov);
    }
}
