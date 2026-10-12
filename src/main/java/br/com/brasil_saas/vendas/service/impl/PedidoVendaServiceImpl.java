package br.com.brasil_saas.vendas.service.impl;

import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.LoteEstoqueRepository;
import br.com.brasil_saas.estoque.repository.EnderecoEstoqueRepository;
import br.com.brasil_saas.estoque.model.ReservaEstoque;
import br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.model.Comissao;
import br.com.brasil_saas.financeiro.repository.ComissaoRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
import br.com.brasil_saas.servicos.service.OrdemServicoService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.vendas.dto.PedidoVendaRequest;
import br.com.brasil_saas.vendas.dto.PedidoVendaResponse;
import br.com.brasil_saas.vendas.model.ItemPedidoVenda;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.model.RegraComissao;
import br.com.brasil_saas.vendas.repository.RegraComissaoRepository;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import br.com.brasil_saas.vendas.service.CalculoDesconto;
import br.com.brasil_saas.vendas.service.PedidoVendaService;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PedidoVendaServiceImpl implements PedidoVendaService {

    private final PedidoVendaRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final OrdemServicoService osService;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final DepositoRepository depositoRepository;
    private final LoteEstoqueRepository loteEstoqueRepository;
    private final EnderecoEstoqueRepository enderecoEstoqueRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final ReservaEstoqueRepository reservaEstoqueRepository;
    private final TituloRepository tituloRepository;
    private final ComissaoRepository comissaoRepository;
    private final RegraComissaoRepository regraComissaoRepository;
    private final FuncionarioRepository funcionarioRepository;
    private final br.com.brasil_saas.financeiro.service.TituloService tituloService;
    private final DocumentoFluxoService documentoFluxoService;
    private final br.com.brasil_saas.financeiro.service.ComissaoService comissaoService;

    @Override
    @Transactional
    public PedidoVendaResponse criar(PedidoVendaRequest request) {
        PedidoVenda pedido = new PedidoVenda();
        if (request.empresaId() == null) throw new BusinessException("Empresa obrigatoria");
        if (request.itens() == null || request.itens().isEmpty()) throw new BusinessException("Pedido precisa possuir ao menos um item");
        String tipo = request.tipo() == null ? "PEDIDO" : request.tipo().trim().toUpperCase(Locale.ROOT);
        String status = request.status() == null ? "ABERTO" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!"PEDIDO".equals(tipo) && !"ORCAMENTO".equals(tipo)) throw new BusinessException("Tipo deve ser PEDIDO ou ORCAMENTO");
        if (!"ABERTO".equals(status)) throw new BusinessException("Novo pedido deve iniciar ABERTO; utilize o fluxo de faturamento ou cancelamento");
        pedido.setEmpresaId(request.empresaId());
        if (request.clienteId() == null || clienteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(request.clienteId(), request.empresaId()).isEmpty()) throw new ResourceNotFoundException("Cliente nao encontrado");
        pedido.setClienteId(request.clienteId());
        pedido.setVendedorId(request.vendedorId());
        pedido.setTipo(tipo);
        pedido.setStatus(status);
        pedido.setDataEmissao(request.dataEmissao() != null ? request.dataEmissao() : LocalDate.now());
        pedido.setDataEntrega(request.dataEntrega());
        pedido.setCondicaoPagamentoId(request.condicaoPagamentoId());
        pedido.setTabelaPrecoId(request.tabelaPrecoId());
        pedido.setCanalVenda(request.canalVenda());
        pedido.setOrigem(request.origem());
        pedido.setObservacao(request.observacao());

        List<ItemPedidoVenda> itens = new ArrayList<>();
        List<CalculoDesconto.Item> paraCalculo = new ArrayList<>();

        int num = 1;
        for (var itemReq : request.itens()) {
                if (itemReq.quantidade() == null || itemReq.quantidade().signum() <= 0) throw new BusinessException("Quantidade deve ser maior que zero");
                if (itemReq.valorUnitario() == null || itemReq.valorUnitario().signum() < 0) throw new BusinessException("Valor unitario invalido");
                ItemPedidoVenda item = new ItemPedidoVenda();
                item.setPedido(pedido);
                item.setEmpresaId(pedido.getEmpresaId());
                item.setNumeroItem(itemReq.numeroItem() != null ? itemReq.numeroItem() : num++);
                item.setProdutoId(itemReq.produtoId());
                item.setServicoId(itemReq.servicoId());
                item.setDescricao(itemReq.descricao());
                item.setQuantidade(itemReq.quantidade());
                item.setUnidade(itemReq.unidade());
                item.setValorUnitario(itemReq.valorUnitario());
                item.setValorDesconto(itemReq.valorDesconto() != null ? itemReq.valorDesconto() : BigDecimal.ZERO);
                item.setValorTotal(itemReq.quantidade().multiply(itemReq.valorUnitario()).subtract(item.getValorDesconto()));
                item.setCriadoEstoque(false);
                itens.add(item);
                paraCalculo.add(new CalculoDesconto.Item(itemReq.quantidade(), itemReq.valorUnitario(), item.getValorDesconto()));
            }

        CalculoDesconto calculo = CalculoDesconto.de(paraCalculo, request.percentualDesconto(), request.valorDesconto(), request.valorFrete());
        pedido.setItens(itens);
        pedido.setValorProdutos(calculo.getValorProdutos());
        pedido.setValorServicos(calculo.getValorServicos());
        pedido.setValorDesconto(calculo.getValorDescontoTotal());
        pedido.setPercentualDesconto(calculo.getPercentualDesconto());
        pedido.setValorFrete(calculo.getValorFrete());
        pedido.setValorTotal(calculo.getValorTotal());
        if (pedido.getNumero() == null || pedido.getNumero().isBlank()) {
            pedido.setNumero("PV-" + System.currentTimeMillis());
        }
        PedidoVenda salvo = pedidoRepository.save(pedido);
        return PedidoVendaResponse.from(salvo);
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoVendaResponse buscarPorId(Long id, Long empresaId) {
        PedidoVenda pedido = pedidoRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de venda nao encontrado"));
        return PedidoVendaResponse.from(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoVendaResponse> listarPorEmpresa(Long empresaId) {
        return pedidoRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId).stream()
                .map(PedidoVendaResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void confirmar(Long id, Long empresaId) {
        PedidoVenda pedido = pedidoBloqueado(id, empresaId);
        if (!"ORCAMENTO".equalsIgnoreCase(pedido.getTipo())) {
            throw new BusinessException("Somente orcamentos podem ser confirmados");
        }
        if (!"ABERTO".equals(pedido.getStatus())) {
            throw new BusinessException("Somente orcamentos ABERTOS podem ser confirmados");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new BusinessException("O orcamento precisa possuir ao menos um item");
        }
        String numAntes = pedido.getNumero();
        pedido.setTipo("PEDIDO");
        pedido.setStatus("ABERTO");
        pedidoRepository.save(pedido);
        documentoFluxoService.ligar(empresaId, null,
                "ORCAMENTO", pedido.getId(), numAntes,
                "PEDIDO_VENDA", pedido.getId(), pedido.getNumero(),
                "CONVERTE");
        reservarEstoqueDoPedido(pedido);
    }

    @Override
    @Transactional
    public void faturar(Long id, Long empresaId) {
        faturar(id, empresaId, false);
    }

    @Override
    @Transactional
    public void faturar(Long id, Long empresaId, boolean forcar) {
        PedidoVenda pedido = pedidoBloqueado(id, empresaId);
        if (!"ABERTO".equals(pedido.getStatus()) || !"PEDIDO".equalsIgnoreCase(pedido.getTipo())) {
            throw new BusinessException("Somente pedidos ABERTOS podem ser faturados");
        }
        if (!forcar) validarCredito(pedido);

        baixarEstoqueDoPedido(pedido);

        Titulo titulo = new Titulo();
        titulo.setEmpresaId(pedido.getEmpresaId());
        titulo.setTipo("R");
        titulo.setNumeroDocumento(pedido.getNumero());
        titulo.setDescricao("Venda - Pedido " + pedido.getNumero());
        Long pessoaId = clienteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(pedido.getClienteId(), pedido.getEmpresaId())
                .map(c -> c.getPessoa() == null ? null : c.getPessoa().getId())
                .orElseThrow(() -> new BusinessException("Cliente " + pedido.getClienteId() + " do pedido " + pedido.getNumero() + " nao tem pessoa vinculada"));
        titulo.setPessoaId(pessoaId);
        titulo.setValorOriginal(pedido.getValorTotal());
        titulo.setValorSaldo(pedido.getValorTotal());
        titulo.setDataEmissao(LocalDate.now());
        titulo.setDataVencimento(LocalDate.now().plusDays(30));
        titulo.setStatus("ABERTO");

        Titulo tituloSalvo = tituloRepository.save(titulo);
        pedido.setTituloId(tituloSalvo.getId());
        documentoFluxoService.ligar(empresaId, null,
                "PEDIDO_VENDA", pedido.getId(), pedido.getNumero(),
                "TITULO", tituloSalvo.getId(), tituloSalvo.getNumeroDocumento(),
                "FATURA");
        documentoFluxoService.ligar(empresaId, null,
                "PEDIDO_VENDA", pedido.getId(), pedido.getNumero(),
                "FATURA_VENDA", pedido.getId(), pedido.getNumero(),
                "FATURA");
        tituloService.gerarParcelas(pedido.getEmpresaId(), tituloSalvo.getId(), pedido.getCondicaoPagamentoId());

        if (pedido.getVendedorId() != null) {
            funcionarioRepository.findById(pedido.getVendedorId()).ifPresent(func -> {
                if ("VENDEDOR".equals(func.getTipoColaborador())) {
                    BigDecimal percentual = calcularPercentualComissao(pedido.getEmpresaId(), func.getId(), pedido.getValorTotal());
                    BigDecimal valorComissao = pedido.getValorTotal().multiply(percentual).movePointLeft(2);
                    Comissao comissao = new Comissao();
                    comissao.setEmpresaId(pedido.getEmpresaId());
                    comissao.setFuncionarioId(func.getId());
                    comissao.setPedidoId(pedido.getId());
                    comissao.setValorVenda(pedido.getValorTotal());
                    comissao.setPercentual(percentual);
                    comissao.setValorComissao(valorComissao);
                    comissao.setStatus("PENDENTE");
                    comissaoRepository.save(comissao);
                }
            });
        }

        pedido.setStatus("FATURADO");
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional
    public Map<String, Object> abrirPosVenda(Long id, Long empresaId, Long usuarioId, String motivo, String equipamento) {
        PedidoVenda pedido = pedidoBloqueado(id, empresaId);
        if (!"FATURADO".equals(pedido.getStatus())) throw new BusinessException("Somente pedido faturado gera pos-venda");
        if (motivo == null || motivo.isBlank()) throw new BusinessException("Motivo obrigatorio");
        var os = osService.abrir(empresaId, usuarioId, new OrdemServicoService.AberturaReq(pedido.getClienteId(), equipamento == null || equipamento.isBlank() ? "Pos-venda pedido " + pedido.getNumero() : equipamento, "Pos-venda do pedido " + pedido.getNumero() + ": " + motivo, null));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("osId", os.id());
        m.put("osNumero", os.numero());
        return m;
    }

    @Override
    @Transactional
    public void cancelar(Long id, Long empresaId) {
        PedidoVenda pedido = pedidoBloqueado(id, empresaId);
        if ("FATURADO".equals(pedido.getStatus())) {
            throw new BusinessException("Pedidos FATURADOS nao podem ser cancelados diretamente");
        }
        reservaEstoqueRepository
                .findByEmpresaIdAndPedidoVendaIdAndDeletedAtIsNull(empresaId, pedido.getId())
                .stream()
                .filter(r -> "RESERVADA".equals(r.getStatus()) || "SEPARACAO".equals(r.getStatus()))
                .forEach(r -> {
                    r.setStatus("LIBERADA");
                    reservaEstoqueRepository.save(r);
                });
        comissaoService.estornarPorPedido(empresaId, pedido.getId(), "Cancelamento do pedido " + pedido.getNumero());
        pedido.setStatus("CANCELADO");
        pedidoRepository.save(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> credito(Long empresaId, Long clienteId) {
        java.math.BigDecimal[] v = somarCredito(empresaId, clienteId);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("limite", v[0]); m.put("emAberto", v[1]); m.put("disponivel", v[0].subtract(v[1]));
        return m;
    }

    private java.math.BigDecimal[] somarCredito(Long empresaId, Long clienteId) {
        var cli = clienteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(clienteId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado"));
        java.math.BigDecimal limite = java.math.BigDecimal.ZERO;
        java.math.BigDecimal emAberto = java.math.BigDecimal.ZERO;
        if (cli.getPessoa() != null && cli.getPessoa().getId() != null) {
            limite = cli.getLimiteCredito() == null ? java.math.BigDecimal.ZERO : cli.getLimiteCredito();
            for (var x : tituloRepository.findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(empresaId, cli.getPessoa().getId())) {
                if (!"R".equals(x.getTipo())) continue;
                if (!"ABERTO".equals(x.getStatus()) && !"PARCIAL".equals(x.getStatus())) continue;
                emAberto = emAberto.add(x.getValorSaldo() == null ? java.math.BigDecimal.ZERO : x.getValorSaldo());
            }
        }
        return new java.math.BigDecimal[]{limite, emAberto};
    }

    @Override
    @Transactional
    public PedidoVendaResponse faturarParcial(Long id, Long empresaId, Map<Long, BigDecimal> quantidades) {
        PedidoVenda pedido = pedidoBloqueado(id, empresaId);
        if ((!"ABERTO".equals(pedido.getStatus()) && !"PARCIAL".equals(pedido.getStatus()))
                || !"PEDIDO".equalsIgnoreCase(pedido.getTipo())) {
            throw new BusinessException("Somente pedidos ABERTOS ou PARCIAIS podem ser faturados parcialmente");
        }
        if (quantidades == null || quantidades.isEmpty())
            throw new BusinessException("Informe ao menos uma quantidade para faturar");
        BigDecimal valorFaturado = BigDecimal.ZERO;
        Map<Long, BigDecimal> porProduto = new java.util.TreeMap<>();
        for (ItemPedidoVenda item : pedido.getItens()) {
            BigDecimal qtd = quantidades.get(item.getId());
            if (qtd == null) continue;
            BigDecimal fat = item.getQuantidadeFaturada() == null ? BigDecimal.ZERO : item.getQuantidadeFaturada();
            BigDecimal restante = item.getQuantidade().subtract(fat);
            if (qtd.signum() <= 0 || qtd.compareTo(restante) > 0)
                throw new BusinessException("Quantidade invalida para faturar no item " + item.getNumeroItem()
                        + ". Restante: " + restante);
            BigDecimal proporcao = qtd.divide(item.getQuantidade(), 6, java.math.RoundingMode.HALF_UP);
            BigDecimal bruto = item.getValorUnitario().multiply(qtd);
            BigDecimal desc = (item.getValorDesconto() == null ? BigDecimal.ZERO : item.getValorDesconto()).multiply(proporcao);
            valorFaturado = valorFaturado.add(bruto.subtract(desc));
            item.setQuantidadeFaturada(fat.add(qtd));
            if (item.getProdutoId() != null) porProduto.merge(item.getProdutoId(), qtd, BigDecimal::add);
        }
        if (valorFaturado.signum() <= 0) throw new BusinessException("Nenhuma quantidade nova foi faturada");
        baixarEstoqueQuantidades(pedido, porProduto, false);

        Titulo titulo = new Titulo();
        titulo.setEmpresaId(pedido.getEmpresaId());
        titulo.setTipo("R");
        titulo.setNumeroDocumento(pedido.getNumero());
        titulo.setDescricao("Venda parcial - Pedido " + pedido.getNumero());
        Long pessoaId = clienteRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(pedido.getClienteId(), pedido.getEmpresaId())
                .map(c -> c.getPessoa() == null ? null : c.getPessoa().getId())
                .orElseThrow(() -> new BusinessException("Cliente sem pessoa vinculada"));
        titulo.setPessoaId(pessoaId);
        titulo.setValorOriginal(valorFaturado);
        titulo.setValorSaldo(valorFaturado);
        titulo.setDataEmissao(LocalDate.now());
        titulo.setDataVencimento(LocalDate.now().plusDays(30));
        titulo.setStatus("ABERTO");
        Titulo salvo = tituloRepository.save(titulo);
        documentoFluxoService.ligar(empresaId, null,
                "PEDIDO_VENDA", pedido.getId(), pedido.getNumero(),
                "TITULO", salvo.getId(), salvo.getNumeroDocumento(), "FATURA_PARCIAL");
        tituloService.gerarParcelas(pedido.getEmpresaId(), salvo.getId(), pedido.getCondicaoPagamentoId());

        final BigDecimal totalFaturado = valorFaturado;
        if (pedido.getVendedorId() != null) {
            funcionarioRepository.findById(pedido.getVendedorId()).ifPresent(func -> {
                if ("VENDEDOR".equals(func.getTipoColaborador())) {
                    BigDecimal percentual = calcularPercentualComissao(pedido.getEmpresaId(), func.getId(), totalFaturado);
                    Comissao comissao = new Comissao();
                    comissao.setEmpresaId(pedido.getEmpresaId());
                    comissao.setFuncionarioId(func.getId());
                    comissao.setPedidoId(pedido.getId());
                    comissao.setValorVenda(totalFaturado);
                    comissao.setPercentual(percentual);
                    comissao.setValorComissao(totalFaturado.multiply(percentual).movePointLeft(2));
                    comissao.setStatus("PENDENTE");
                    comissaoRepository.save(comissao);
                }
            });
        }

        boolean completo = pedido.getItens().stream().allMatch(it -> {
            BigDecimal fat = it.getQuantidadeFaturada() == null ? BigDecimal.ZERO : it.getQuantidadeFaturada();
            return fat.compareTo(it.getQuantidade()) >= 0;
        });
        for (ItemPedidoVenda item : pedido.getItens()) {
            if (item.getProdutoId() == null) continue;
            BigDecimal fat = item.getQuantidadeFaturada() == null ? BigDecimal.ZERO : item.getQuantidadeFaturada();
            if (fat.compareTo(item.getQuantidade()) >= 0) item.setCriadoEstoque(true);
        }
        pedido.setStatus(completo ? "FATURADO" : "PARCIAL");
        return PedidoVendaResponse.from(pedidoRepository.save(pedido));
    }

    @Override
    @Transactional
    public java.util.Map<String, Object> vendaRapida(Long empresaId, PedidoVendaService.VendaRapidaRequest req) {
        if (req.itens() == null || req.itens().isEmpty())
            throw new BusinessException("Venda rapida precisa de ao menos um item");
        if (req.valorRecebido() == null || req.valorRecebido().signum() <= 0)
            throw new BusinessException("Informe o valor recebido");
        var itensReq = new ArrayList<br.com.brasil_saas.vendas.dto.ItemPedidoVendaRequest>();
        for (var it : req.itens()) {
            if (it.quantidade() == null || it.quantidade().signum() <= 0)
                throw new BusinessException("Quantidade invalida");
            if (it.valorUnitario() == null || it.valorUnitario().signum() < 0)
                throw new BusinessException("Valor unitario invalido");
            itensReq.add(new br.com.brasil_saas.vendas.dto.ItemPedidoVendaRequest(
                    null, it.produtoId(), null, it.descricao(), it.quantidade(),
                    it.unidade() == null ? "UN" : it.unidade(), it.valorUnitario(), BigDecimal.ZERO));
        }
        var pedidoReq = new PedidoVendaRequest(empresaId, req.clienteId(), req.vendedorId(),
                "PEDIDO", "ABERTO", LocalDate.now(), null, req.condicaoPagamentoId(), null,
                null, "PDV", "PDV", "Venda rapida de balcao", null, null, itensReq);
        PedidoVendaResponse criado = criar(pedidoReq);
        PedidoVenda pedido = pedidoBloqueado(criado.id(), empresaId);
        reservarEstoqueDoPedido(pedido);
        faturar(criado.id(), empresaId, true);
        pedido = pedidoBloqueado(criado.id(), empresaId);
        if (req.valorRecebido().compareTo(pedido.getValorTotal()) < 0)
            throw new BusinessException("Valor recebido insuficiente: total " + pedido.getValorTotal());
        var baixa = tituloService.baixar(empresaId, pedido.getTituloId(),
                new br.com.brasil_saas.financeiro.dto.FinanceiroDtos.BaixaRequest(
                        null, req.contaBancariaId(), req.tipoPagamentoId(), LocalDate.now(),
                        pedido.getValorTotal(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "PDV"));
        BigDecimal troco = req.valorRecebido().subtract(pedido.getValorTotal());
        return Map.of("pedidoId", pedido.getId(), "tituloId", pedido.getTituloId(),
                "total", pedido.getValorTotal(), "valorRecebido", req.valorRecebido(),
                "troco", troco, "statusTitulo", baixa.statusTitulo());
    }

    private void validarCredito(PedidoVenda pedido) {
        if (pedido.getClienteId() == null) return;
        // Serializa faturamentos concorrentes do mesmo cliente antes de somar os recebiveis.
        clienteRepository.findForUpdate(pedido.getClienteId(), pedido.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente nao encontrado"));
        java.math.BigDecimal[] v = somarCredito(pedido.getEmpresaId(), pedido.getClienteId());
        java.math.BigDecimal pedidoValor = pedido.getValorTotal() == null ? java.math.BigDecimal.ZERO : pedido.getValorTotal();
        if (v[1].add(pedidoValor).compareTo(v[0]) > 0) throw new BusinessException("Limite de credito estourado: disponivel " + v[0].subtract(v[1]));
    }

    private PedidoVenda pedidoBloqueado(Long id, Long empresaId) {
        return pedidoRepository.findByIdForUpdateAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de venda nao encontrado"));
    }

    private BigDecimal calcularPercentualComissao(Long empresaId, Long vendedorId, BigDecimal valorVenda) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = hoje.plusMonths(1).withDayOfMonth(1).atStartOfDay();
        BigDecimal acumulado = comissaoRepository.sumVendasPeriodo(empresaId, vendedorId, inicioMes, fimMes);
        acumulado = acumulado == null ? BigDecimal.ZERO : acumulado;
        BigDecimal acumuladoComVenda = acumulado.add(valorVenda);
        List<RegraComissao> regras = regraComissaoRepository.findByEmpresaIdAndVendedorIdAndAtivoTrueOrderByFaixaValorMinAsc(empresaId, vendedorId);
        if (regras.isEmpty()) {
            regras = regraComissaoRepository.findByEmpresaIdAndAtivoTrueOrderByFaixaValorMinAsc(empresaId);
        }
        return regras.stream()
                .filter(r -> (r.getVigenciaInicio() == null || !hoje.isBefore(r.getVigenciaInicio())) && (r.getVigenciaFim() == null || !hoje.isAfter(r.getVigenciaFim())))
                .filter(r -> r.getFaixaValorMin() == null || acumuladoComVenda.compareTo(r.getFaixaValorMin()) >= 0)
                .filter(r -> r.getFaixaValorMax() == null || acumuladoComVenda.compareTo(r.getFaixaValorMax()) <= 0)
                .filter(r -> r.getMetaValor() == null || acumuladoComVenda.compareTo(r.getMetaValor()) >= 0)
                .map(RegraComissao::getPercentual)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(() -> funcionarioRepository.findById(vendedorId).map(f -> f.getPercentualComissao() == null ? BigDecimal.ZERO : f.getPercentualComissao()).orElse(BigDecimal.ZERO));
    }

    private void reservarEstoqueDoPedido(PedidoVenda pedido) {
        var depositos = depositoRepository.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(pedido.getEmpresaId());
        if (depositos.isEmpty()) return;
        depositos.sort((a, b) -> {
            boolean pa = "PADRAO".equals(a.getTipo());
            boolean pb = "PADRAO".equals(b.getTipo());
            if (pa != pb) return pa ? -1 : 1;
            return a.getId().compareTo(b.getId());
        });
        for (ItemPedidoVenda item : pedido.getItens()) {
            if (item.getProdutoId() == null) continue;
            BigDecimal restante = item.getQuantidade() == null ? BigDecimal.ZERO : item.getQuantidade();
            BigDecimal totalDisponivel = BigDecimal.ZERO;
            for (var deposito : depositos) {
                if (restante.signum() <= 0) break;
                SaldoEstoque saldo = saldoEstoqueRepository.findForUpdate(pedido.getEmpresaId(), deposito.getId(), item.getProdutoId()).orElse(null);
                if (saldo == null || saldo.getQuantidade() == null) continue;
                BigDecimal reservado = reservaEstoqueRepository.sumAtivas(pedido.getEmpresaId(), deposito.getId(), item.getProdutoId());
                reservado = reservado == null ? BigDecimal.ZERO : reservado;
                BigDecimal disponivel = saldo.getQuantidade().subtract(reservado);
                totalDisponivel = totalDisponivel.add(disponivel.signum() > 0 ? disponivel : BigDecimal.ZERO);
                if (disponivel.signum() <= 0) continue;
                BigDecimal alocar = restante.min(disponivel);
                ReservaEstoque reserva = new ReservaEstoque();
                reserva.setEmpresaId(pedido.getEmpresaId());
                reserva.setDepositoId(deposito.getId());
                reserva.setProdutoId(item.getProdutoId());
                reserva.setQuantidade(alocar);
                reserva.setPedidoVendaId(pedido.getId());
                reserva.setStatus("RESERVADA");
                reserva.setDataReserva(LocalDateTime.now());
                reservaEstoqueRepository.save(reserva);
                restante = restante.subtract(alocar);
            }
            if (restante.signum() > 0) {
                throw new BusinessException("Estoque insuficiente para reservar produto " + item.getProdutoId() + " (disponivel " + totalDisponivel + ")");
            }
        }
    }

    private void baixarEstoqueDoPedido(PedidoVenda pedido) {
        Map<Long, BigDecimal> quantidades = new java.util.TreeMap<>();
        for (ItemPedidoVenda item : pedido.getItens()) {
            if (item.getProdutoId() != null && !Boolean.TRUE.equals(item.getCriadoEstoque())) {
                if (item.getQuantidade() == null || item.getQuantidade().signum() <= 0)
                    throw new BusinessException("Quantidade invalida para faturamento");
                quantidades.merge(item.getProdutoId(), item.getQuantidade(), BigDecimal::add);
            }
        }
        baixarEstoqueQuantidades(pedido, quantidades, true);
    }

    private void baixarEstoqueQuantidades(PedidoVenda pedido, Map<Long, BigDecimal> quantidades, boolean marcaCompleto) {
        var reservas = reservaEstoqueRepository.findByPedidoForUpdate(pedido.getEmpresaId(), pedido.getId());
        List<Alocacao> alocacoes = new ArrayList<>();
        for (var produto : quantidades.entrySet()) {
            BigDecimal reservada = BigDecimal.ZERO;
            for (ReservaEstoque reserva : reservas) {
                // WMS pode concluir a expedicao antes do faturamento. CONSUMIDA nao significa
                // que o saldo ja foi baixado: essa baixa pertence ao faturamento do pedido.
                if (!produto.getKey().equals(reserva.getProdutoId()) ||
                        !List.of("RESERVADA", "SEPARACAO", "CONSUMIDA").contains(reserva.getStatus())) continue;
                if (reserva.getQuantidade() == null || reserva.getQuantidade().signum() <= 0 || reserva.getDepositoId() == null)
                    throw new BusinessException("Reserva do pedido possui quantidade/deposito invalidos");
                reservada = reservada.add(reserva.getQuantidade());
                alocacoes.add(new Alocacao(produto.getKey(), reserva.getDepositoId(), reserva.getLoteId(),
                        reserva.getEnderecoId(), reserva.getQuantidade(), reserva));
            }
            if (reservada.compareTo(produto.getValue()) > 0)
                throw new BusinessException("Reservas excedem quantidade do pedido para produto " + produto.getKey());
            BigDecimal restante = produto.getValue().subtract(reservada);
            if (restante.signum() > 0) {
                Long deposito = depositoRepository.findFirstByEmpresaIdAndTipoAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(pedido.getEmpresaId(), "PADRAO")
                        .or(() -> depositoRepository.findFirstByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(pedido.getEmpresaId()))
                        .map(d -> d.getId()).orElseThrow(() -> new BusinessException("Nenhum deposito ativo para baixa de estoque"));
                alocacoes.add(new Alocacao(produto.getKey(), deposito, null, null, restante, null));
            }
        }
        // A ordem comum de travas evita pedidos concorrentes bloqueando depositos em ordem inversa.
        alocacoes.sort(java.util.Comparator.comparing(Alocacao::depositoId).thenComparing(Alocacao::produtoId));
        for (Alocacao alocacao : alocacoes) baixarEstoque(pedido, alocacao);
        // Em faturamento integral, a baixa fisica concluida precisa marcar os itens.
        // quantidadeFaturada e preenchida pelo fluxo de notas parciais, nao pelo
        // faturamento integral. Sem esta marca o mesmo item poderia baixar duas vezes.
        if (marcaCompleto) {
            for (ItemPedidoVenda item : pedido.getItens()) {
                if (item.getProdutoId() != null && quantidades.containsKey(item.getProdutoId())) {
                    item.setCriadoEstoque(true);
                }
            }
        }
    }

    private record Alocacao(Long produtoId, Long depositoId, Long loteId, Long enderecoId,
                            BigDecimal quantidade, ReservaEstoque reserva) {}

    private void baixarEstoque(PedidoVenda pedido, Alocacao a) {
        Long empresaId = pedido.getEmpresaId();
        depositoRepository.findByIdAndEmpresaIdAndAtivoTrue(a.depositoId(), empresaId)
                .filter(d -> d.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("Deposito da alocacao nao esta ativo nesta empresa"));
        SaldoEstoque saldo = saldoEstoqueRepository.findForUpdate(empresaId, a.depositoId(), a.produtoId())
                .orElseThrow(() -> new BusinessException("Sem saldo do produto " + a.produtoId()));
        BigDecimal outros = reservaEstoqueRepository.sumAtivasDeOutrosPedidos(empresaId, a.depositoId(), a.produtoId(), pedido.getId());
        if (saldo.getQuantidade().subtract(zero(outros)).compareTo(a.quantidade()) < 0)
            throw new BusinessException("Saldo insuficiente do produto " + a.produtoId());
        if (a.enderecoId() != null) {
            var endereco = enderecoEstoqueRepository.findByIdAndEmpresaIdAndAtivoTrue(a.enderecoId(), empresaId)
                    .filter(e -> e.getDeletedAt() == null)
                    .orElseThrow(() -> new BusinessException("Endereco da reserva nao esta ativo nesta empresa"));
            if (!a.depositoId().equals(endereco.getDepositoId()))
                throw new BusinessException("Endereco da reserva pertence a outro deposito");
            var posicoes = movimentacaoRepository.saldosPorEndereco(empresaId, a.depositoId(), a.produtoId(), null);
            BigDecimal totalEndereco = posicoes.stream().filter(x -> a.enderecoId().equals(x.getEnderecoId()))
                    .map(MovimentacaoEstoqueRepository.EnderecoSaldo::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal outrosEndereco = reservaEstoqueRepository.sumAtivasDeOutrosPedidosPorEndereco(
                    empresaId, a.depositoId(), a.produtoId(), a.enderecoId(), null, pedido.getId());
            if (totalEndereco.subtract(zero(outrosEndereco)).compareTo(a.quantidade()) < 0)
                throw new BusinessException("Saldo insuficiente no endereco reservado");
            if (a.loteId() != null) {
                BigDecimal saldoLoteEndereco = posicoes.stream()
                        .filter(x -> a.enderecoId().equals(x.getEnderecoId()) && a.loteId().equals(x.getLoteId()))
                        .map(MovimentacaoEstoqueRepository.EnderecoSaldo::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal outrosLoteEndereco = reservaEstoqueRepository.sumAtivasDeOutrosPedidosPorEndereco(
                        empresaId, a.depositoId(), a.produtoId(), a.enderecoId(), a.loteId(), pedido.getId());
                if (saldoLoteEndereco.subtract(zero(outrosLoteEndereco)).compareTo(a.quantidade()) < 0)
                    throw new BusinessException("Saldo insuficiente no lote/endereco reservado");
            }
        }
        if (a.loteId() != null) {
            var lote = loteEstoqueRepository.findForUpdate(empresaId, a.loteId())
                    .orElseThrow(() -> new BusinessException("Lote da reserva nao encontrado"));
            if (!a.produtoId().equals(lote.getProdutoId()) || !a.depositoId().equals(lote.getDepositoId()) || !"ATIVO".equals(lote.getStatus()))
                throw new BusinessException("Lote da reserva incompativel ou inativo");
            BigDecimal outrosLote = reservaEstoqueRepository.sumAtivasDeOutrosPedidosPorLote(
                    empresaId, a.depositoId(), a.produtoId(), a.loteId(), pedido.getId());
            if (lote.getQuantidade().subtract(zero(outrosLote)).compareTo(a.quantidade()) < 0)
                throw new BusinessException("Saldo insuficiente no lote reservado");
            lote.setQuantidade(lote.getQuantidade().subtract(a.quantidade()));
            loteEstoqueRepository.save(lote);
        }
        saldo.setQuantidade(saldo.getQuantidade().subtract(a.quantidade()));
        saldoEstoqueRepository.save(saldo);
        MovimentacaoEstoque mov = new MovimentacaoEstoque();
        mov.setEmpresaId(empresaId);
        mov.setDepositoId(a.depositoId());
        mov.setProdutoId(a.produtoId());
        mov.setLoteId(a.loteId());
        mov.setEnderecoId(a.enderecoId());
        mov.setTipo("SAIDA");
        mov.setOrigem("PEDIDO_VENDA");
        mov.setOrigemId(pedido.getId());
        mov.setQuantidade(a.quantidade().negate());
        mov.setSaldoApos(saldo.getQuantidade());
        mov.setObservacao("Baixa por venda");
        movimentacaoRepository.save(mov);
        if (a.reserva() != null && !"CONSUMIDA".equals(a.reserva().getStatus())) {
            a.reserva().setStatus("CONSUMIDA");
            reservaEstoqueRepository.save(a.reserva());
        }
    }

    private BigDecimal zero(BigDecimal valor) { return valor == null ? BigDecimal.ZERO : valor; }
}
