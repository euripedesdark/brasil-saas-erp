package br.com.brasil_saas.compras.service;
import br.com.brasil_saas.compras.model.DevCompra;
import br.com.brasil_saas.compras.model.DevCompraItem;
import br.com.brasil_saas.compras.model.ItemPedidoCompra;
import br.com.brasil_saas.compras.model.PedidoCompra;
import br.com.brasil_saas.compras.repository.DevCompraItemRepository;
import br.com.brasil_saas.compras.repository.DevCompraRepository;
import br.com.brasil_saas.compras.repository.PedidoCompraRepository;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.contabilidade.service.ContabilidadeService;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.BaixaRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.financeiro.service.TituloService;
import br.com.brasil_saas.shared.exception.BusinessException;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
@Service @RequiredArgsConstructor
public class DevCompraService {
    private final DevCompraRepository devolucoes;
    private final DevCompraItemRepository itens;
    private final PedidoCompraRepository pedidos;
    private final SaldoEstoqueRepository saldos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final br.com.brasil_saas.estoque.repository.DepositoRepository depositos;
    private final br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository reservas;
    private final TituloRepository tituloRepository;
    private final BaixaRepository baixaRepository;
    private final TituloService tituloService;
    private final DocumentoFluxoService documentoFluxoService;
    private final ContabilidadeService contabilidadeService;
    @Transactional(readOnly = true)
    public List<DevCompra> listar(Long empresaId) { return devolucoes.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresaId); }
    @Transactional(readOnly = true)
    public List<DevCompraItem> itensDe(Long empresaId, Long id) { porEmpresa(empresaId, id); return itens.findByEmpresaIdAndDevolucaoIdAndDeletedAtIsNull(empresaId, id); }
    @Transactional
    public DevCompra solicitar(Long empresaId, Long pedidoId, String motivo, List<Map<String, Object>> itensReq) {
        PedidoCompra pedido = pedidos.findByIdForUpdateAndEmpresaId(pedidoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de compra nao encontrado"));
        if (!"RECEBIDO".equals(pedido.getStatus()) && !"PARCIAL".equals(pedido.getStatus()))
            throw new BusinessException("Somente pedidos recebidos podem ser devolvidos");
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 500)
            throw new BusinessException("Motivo obrigatorio, com no maximo 500 caracteres");
        if (itensReq == null || itensReq.isEmpty()) throw new BusinessException("Informe ao menos um item");
        Map<Long, BigDecimal> solicitadas = new LinkedHashMap<>();
        for (var r : itensReq) {
            try {
                if (r == null || r.get("produtoId") == null || r.get("quantidade") == null)
                    throw new BusinessException("Item invalido");
                Long produtoId = Long.valueOf(String.valueOf(r.get("produtoId")));
                BigDecimal qtd = quantidade(new BigDecimal(String.valueOf(r.get("quantidade"))));
                solicitadas.merge(produtoId, qtd, BigDecimal::add);
            } catch (NumberFormatException e) { throw new BusinessException("Item invalido"); }
        }
        Map<Long, BigDecimal> recebidas = new LinkedHashMap<>();
        if (pedido.getItens() != null) for (ItemPedidoCompra ip : pedido.getItens())
            if (ip.getProdutoId() != null) recebidas.merge(ip.getProdutoId(),
                    ip.getQuantidadeRecebida() == null ? BigDecimal.ZERO : ip.getQuantidadeRecebida(), BigDecimal::add);
        Map<Long, BigDecimal> comprometidas = new LinkedHashMap<>();
        for (DevCompra anterior : devolucoes.findByPedidoIdAndEmpresaIdAndDeletedAtIsNull(pedidoId, empresaId)) {
            if ("CANCELADA".equals(anterior.getStatus())) continue;
            for (DevCompraItem it : itens.findByEmpresaIdAndDevolucaoIdAndDeletedAtIsNull(empresaId, anterior.getId()))
                comprometidas.merge(it.getProdutoId(), quantidade(it.getQuantidade()), BigDecimal::add);
        }
        for (var e : solicitadas.entrySet())
            if (e.getValue().add(comprometidas.getOrDefault(e.getKey(), BigDecimal.ZERO))
                    .compareTo(recebidas.getOrDefault(e.getKey(), BigDecimal.ZERO)) > 0)
                throw new BusinessException("Quantidade acima da recebida");
        DevCompra d = new DevCompra(); d.setEmpresaId(empresaId); d.setPedidoId(pedidoId);
        d.setMotivo(motivo.trim()); d = devolucoes.save(d);
        for (var e : solicitadas.entrySet()) {
            DevCompraItem it = new DevCompraItem(); it.setEmpresaId(empresaId); it.setDevolucaoId(d.getId());
            it.setProdutoId(e.getKey()); it.setQuantidade(e.getValue()); itens.save(it);
        }
        return d;
    }
    @Transactional
    public DevCompra devolver(Long empresaId, Long id) {
        DevCompra d = bloqueada(empresaId, id);
        if (!"SOLICITADA".equals(d.getStatus())) throw new BusinessException("Somente devolucao SOLICITADA pode ser devolvida");
        var linhas = itens.findByEmpresaIdAndDevolucaoIdAndDeletedAtIsNull(empresaId, id);
        if (linhas.isEmpty()) throw new BusinessException("Devolucao sem itens");
        Map<Long, BigDecimal> quantidades = new java.util.TreeMap<>();
        for (var it : linhas) {
            if (it.getProdutoId() == null) throw new BusinessException("Item sem produto");
            quantidades.merge(it.getProdutoId(), quantidade(it.getQuantidade()), BigDecimal::add);
        }
        Long depositoId = depositos.findFirstByEmpresaIdAndTipoAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(empresaId, "PADRAO")
                .or(() -> depositos.findFirstByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(empresaId))
                .orElseThrow(() -> new BusinessException("Nenhum deposito ativo para devolver")).getId();
        depositos.findAtivoForUpdate(depositoId, empresaId)
                .orElseThrow(() -> new BusinessException("Deposito indisponivel"));
        Map<Long, SaldoEstoque> bloqueados = new LinkedHashMap<>();
        for (var e : quantidades.entrySet()) {
            var saldo = saldos.findForUpdate(empresaId, depositoId, e.getKey())
                    .orElseThrow(() -> new BusinessException("Estoque insuficiente para devolver"));
            var reservada = reservas.sumAtivas(empresaId, depositoId, e.getKey());
            var disponivel = (saldo.getQuantidade() == null ? BigDecimal.ZERO : saldo.getQuantidade())
                    .subtract(reservada == null ? BigDecimal.ZERO : reservada);
            if (disponivel.compareTo(e.getValue()) < 0) throw new BusinessException("Estoque livre insuficiente para devolver");
            bloqueados.put(e.getKey(), saldo);
        }
        for (var e : quantidades.entrySet()) {
            var saldo = bloqueados.get(e.getKey()); saldo.setQuantidade(saldo.getQuantidade().subtract(e.getValue()));
            saldos.save(saldo);
            MovimentacaoEstoque mov = new MovimentacaoEstoque(); mov.setEmpresaId(empresaId);
            mov.setProdutoId(e.getKey()); mov.setDepositoId(depositoId); mov.setTipo("SAIDA");
            mov.setOrigem("DEVOLUCAO_COMPRA"); mov.setOrigemId(id); mov.setQuantidade(e.getValue().negate());
            mov.setSaldoApos(saldo.getQuantidade()); mov.setObservacao("Devolucao ao fornecedor"); movimentacoes.save(mov);
        }
        PedidoCompra pedido = pedidos.findByIdForUpdateAndEmpresaId(d.getPedidoId(), empresaId)
                .orElse(null);
        integrarFinanceiro(empresaId, d, linhas, pedido);
        d.setStatus("DEVOLVIDA");
        d.setDevolvidaEm(LocalDateTime.now());
        return devolucoes.save(d);
    }
    @Transactional
    public DevCompra cancelar(Long empresaId, Long id) {
        DevCompra d = bloqueada(empresaId, id);
        if (!"SOLICITADA".equals(d.getStatus())) throw new BusinessException("Somente devolucao SOLICITADA pode ser cancelada");
        d.setStatus("CANCELADA");
        return devolucoes.save(d);
    }
    private BigDecimal quantidade(BigDecimal valor) {
        if (valor == null || valor.signum() <= 0 || valor.stripTrailingZeros().scale() > 3)
            throw new BusinessException("Quantidade deve ser positiva, com no maximo 3 casas decimais");
        return valor;
    }
    private DevCompra bloqueada(Long empresaId, Long id) {
        return devolucoes.findForUpdate(id, empresaId).orElseThrow(() -> new ResourceNotFoundException("Devolucao nao encontrada"));
    }
    private DevCompra porEmpresa(Long empresaId, Long id) {
        return devolucoes.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId).orElseThrow(() -> new ResourceNotFoundException("Devolucao nao encontrada"));
    }

    /**
     * Encadeamento automatico devolucao -> financeiro (compra).
     * Calcula o valor devolvido proporcional aos itens recebidos do pedido
     * (valorTotal da linha rateado pela quantidade recebida) e:
     * - titulo P ABERTO/PARCIAL com saldo: cria Baixa de ajuste sem conta
     *   bancaria, via TituloService.baixar (mesma trava pessimista, rateio
     *   de parcelas, bloqueio por conferencia pendente preservado);
     * - titulo BAIXADO/saldo zerado: cria titulo R (credito a receber do
     *   fornecedor);
     * - sem titulo ou titulo CANCELADO: so registra o fluxo.
     * Idempotente por marcador. Nao emite documento fiscal nem contabiliza:
     * o titulo/Baixa seguem o caminho existente (gerarDeTitulo) e a NF de
     * devolucao permanece pendencia explicita (Fiscal por ultimo).
     */
    private void integrarFinanceiro(Long empresaId, DevCompra d,
                                    java.util.List<DevCompraItem> linhas, PedidoCompra pedido) {
        if (pedido == null) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                    "PEDIDO_COMPRA", d.getPedidoId(), null,
                    "DEVOLVIDA_SEM_PEDIDO");
            return;
        }
        BigDecimal valorDevolvido = calcularValorDevolvido(pedido, linhas);
        if (pedido.getTituloId() == null) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                    "PEDIDO_COMPRA", pedido.getId(), pedido.getNumero(),
                    "DEVOLVIDA_SEM_TITULO");
            return;
        }
        String marcador = "DEVOLUCAO_COMPRA#" + d.getId();
        Titulo titulo = tituloRepository.findForUpdate(pedido.getTituloId(), empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Titulo do pedido inexistente"));
        if ("CANCELADO".equalsIgnoreCase(titulo.getStatus())) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                    "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                    "DEVOLVIDA_TITULO_CANCELADO");
            return;
        }
        BigDecimal saldo = titulo.getValorSaldo() == null ? BigDecimal.ZERO : titulo.getValorSaldo();
        boolean ajustavel = saldo.signum() > 0
                && ("ABERTO".equalsIgnoreCase(titulo.getStatus()) || "PARCIAL".equalsIgnoreCase(titulo.getStatus()));
        if (ajustavel && valorDevolvido.signum() > 0) {
            BigDecimal valorBaixa = valorDevolvido.min(saldo);
            boolean jaAjustado = baixaRepository.findByTituloIdAndDeletedAtIsNull(titulo.getId()).stream()
                    .anyMatch(b -> b.getObservacao() != null && b.getObservacao().contains(marcador));
            if (!jaAjustado && valorBaixa.signum() > 0) {
                tituloService.baixar(empresaId, titulo.getId(), new BaixaRequest(
                        null, null, null, LocalDate.now(), valorBaixa,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        marcador + " DC-" + d.getId()));
            }
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                    "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                    "AJUSTE_DEVOLUCAO");
            ajustarContabil(empresaId, d, titulo, titulo.getId(), valorBaixa);
            BigDecimal restante = valorDevolvido.subtract(valorBaixa);
            if (restante.signum() > 0) {
                criarTituloCredito(empresaId, d, pedido, titulo, restante);
            }
            return;
        }
        if (valorDevolvido.signum() > 0) {
            criarTituloCredito(empresaId, d, pedido, titulo, valorDevolvido);
            return;
        }
        documentoFluxoService.ligar(empresaId, null,
                "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                "DEVOLVIDA_SEM_AJUSTE");
    }

    private void criarTituloCredito(Long empresaId, DevCompra d, PedidoCompra pedido, Titulo origem, BigDecimal valor) {
        String numeroDoc = "CRED-DC-" + d.getId();
        boolean jaExiste = !tituloRepository.findByEmpresaIdAndNumeroDocumentoAndDeletedAtIsNull(empresaId, numeroDoc).isEmpty();
        if (jaExiste) {
            return;
        }
        Titulo r = new Titulo();
        r.setEmpresaId(empresaId);
        r.setTipo("R");
        r.setNumeroDocumento(numeroDoc);
        r.setDescricao("Credito devolucao ao fornecedor DC-" + d.getId()
                + " pedido " + (pedido.getNumero() == null ? ("#" + pedido.getId()) : pedido.getNumero()));
        r.setPessoaId(origem.getPessoaId());
        r.setValorOriginal(valor.setScale(2, RoundingMode.HALF_UP));
        r.setValorSaldo(valor.setScale(2, RoundingMode.HALF_UP));
        r.setDataEmissao(LocalDate.now());
        r.setDataVencimento(LocalDate.now().plusDays(30));
        r.setStatus("ABERTO");
        Titulo salvo = tituloRepository.save(r);
        documentoFluxoService.ligar(empresaId, null,
                "DEVOLUCAO_COMPRA", d.getId(), "DC-" + d.getId(),
                "TITULO", salvo.getId(), salvo.getNumeroDocumento(),
                "CREDITO_DEVOLUCAO");
        ajustarContabil(empresaId, d, origem, salvo.getId(), valor);
    }

    /**
     * Ajuste contabil do valor devolvido: espelha proporcionalmente os
     * lancamentos do titulo de origem para o titulo destino. Periodo
     * fechado ou origem nunca contabilizada viram pendencia explicita
     * na trilha, sem travar a devolucao fisica nem o financeiro.
     */
    private void ajustarContabil(Long empresaId, DevCompra d,
            Titulo origem, Long tituloDestinoId, BigDecimal valorAjuste) {
        String numero = "DC-" + d.getId();
        BigDecimal original = origem.getValorOriginal();
        if (original == null || original.signum() <= 0 || valorAjuste == null || valorAjuste.signum() <= 0) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), numero,
                    "TITULO", tituloDestinoId, "SEM_VALOR_ORIGINAL",
                    "AJUSTE_CONTABIL_PENDENTE");
            return;
        }
        BigDecimal proporcao = valorAjuste.divide(original, 10, RoundingMode.HALF_UP);
        if (proporcao.compareTo(BigDecimal.ONE) > 0) proporcao = BigDecimal.ONE;
        var espelho = contabilidadeService.espelharAjusteDevolucao(empresaId, origem.getId(), tituloDestinoId,
                proporcao, "Ajuste devolucao ao fornecedor " + numero);
        if ("APLICADO".equals(espelho.situacao())) {
            for (var lancado : espelho.lancamentos()) {
                documentoFluxoService.ligar(empresaId, null,
                        "DEVOLUCAO_COMPRA", d.getId(), numero,
                        "TITULO", tituloDestinoId, lancado.getId() + ":" + lancado.getPeriodo(),
                        "AJUSTE_CONTABIL");
            }
        } else {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_COMPRA", d.getId(), numero,
                    "TITULO", tituloDestinoId, espelho.situacao(),
                    "AJUSTE_CONTABIL_PENDENTE");
        }
    }

    /**
     * Valor devolvido proporcional ao recebido: por produto, valor recebido
     * da linha (valorTotal rateado pela quantidade recebida) vezes
     * (qtdDevolvida / qtdRecebida), HALF_UP em 2 casas.
     */
    private BigDecimal calcularValorDevolvido(PedidoCompra pedido, java.util.List<DevCompraItem> linhas) {
        Map<Long, BigDecimal> qtdRecebida = new HashMap<>();
        Map<Long, BigDecimal> valorRecebido = new HashMap<>();
        if (pedido.getItens() != null) for (ItemPedidoCompra ip : pedido.getItens()) {
            if (ip.getProdutoId() == null) continue;
            BigDecimal qtd = ip.getQuantidade() == null ? BigDecimal.ZERO : ip.getQuantidade();
            BigDecimal rec = ip.getQuantidadeRecebida() == null ? BigDecimal.ZERO : ip.getQuantidadeRecebida();
            BigDecimal tot = ip.getValorTotal() == null ? BigDecimal.ZERO : ip.getValorTotal();
            if (qtd.signum() <= 0 || rec.signum() <= 0) continue;
            BigDecimal valorLinhaRecebida = tot.multiply(rec).divide(qtd, 10, RoundingMode.HALF_UP);
            qtdRecebida.merge(ip.getProdutoId(), rec, BigDecimal::add);
            valorRecebido.merge(ip.getProdutoId(), valorLinhaRecebida, BigDecimal::add);
        }
        Map<Long, BigDecimal> qtdDev = new HashMap<>();
        for (var it : linhas) {
            if (it.getProdutoId() == null || it.getQuantidade() == null) continue;
            qtdDev.merge(it.getProdutoId(), it.getQuantidade(), BigDecimal::add);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var e : qtdDev.entrySet()) {
            BigDecimal base = qtdRecebida.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal valor = valorRecebido.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (base.signum() <= 0 || e.getValue().signum() <= 0) continue;
            BigDecimal proporcao = e.getValue().divide(base, 10, RoundingMode.HALF_UP);
            if (proporcao.compareTo(BigDecimal.ONE) > 0) proporcao = BigDecimal.ONE;
            total = total.add(valor.multiply(proporcao));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
