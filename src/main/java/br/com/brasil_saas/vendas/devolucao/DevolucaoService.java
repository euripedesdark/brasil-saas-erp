package br.com.brasil_saas.vendas.devolucao;
import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import br.com.brasil_saas.estoque.model.SaldoEstoque;
import br.com.brasil_saas.estoque.repository.MovimentacaoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.core.service.DocumentoFluxoService;
import br.com.brasil_saas.contabilidade.service.ContabilidadeService;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.BaixaRequest;
import br.com.brasil_saas.financeiro.model.Titulo;
import br.com.brasil_saas.financeiro.repository.BaixaRepository;
import br.com.brasil_saas.financeiro.repository.TituloRepository;
import br.com.brasil_saas.financeiro.service.TituloService;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class DevolucaoService {
    private final VenDevolucaoRepository devolucoes;
    private final VenDevolucaoItemRepository itens;
    private final PedidoVendaRepository pedidos;
    private final SaldoEstoqueRepository saldos;
    private final MovimentacaoEstoqueRepository movimentacoes;
    private final DepositoRepository depositos;
    private final TituloRepository tituloRepository;
    private final BaixaRepository baixaRepository;
    private final TituloService tituloService;
    private final DocumentoFluxoService documentoFluxoService;
    private final ContabilidadeService contabilidadeService;
    private final br.com.brasil_saas.financeiro.service.ComissaoService comissaoService;
    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    private void garantir(Optional<?> o, String msg) {
        o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }
    public List<VenDevolucao> listar(Long empresaId) { return devolucoes.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresaId); }
    public List<VenDevolucaoItem> itens(Long empresaId, Long id) {
        garantir(devolucoes.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Devolucao inexistente");
        return itens.findByDevolucaoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId);
    }
    @Transactional public VenDevolucao solicitar(Long empresaId, Long pedidoId, String motivo, Map<Long, BigDecimal> itensQtd) {
        // A trava do pedido serializa solicitacoes concorrentes sobre o mesmo pedido.
        PedidoVenda p = exigir(pedidos.findByIdForUpdateAndEmpresaId(pedidoId, empresaId), "Pedido inexistente");
        if (!"FATURADO".equals(p.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Somente pedido faturado");
        if (motivo == null || motivo.isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Motivo obrigatorio");
        if (itensQtd == null || itensQtd.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Informe ao menos um item");
        if (motivo.length() > 100) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Motivo deve ter no maximo 100 caracteres");
        // Quantidade ja comprometida por outras devolucoes (solicitadas, aprovadas ou recebidas).
        Map<Long, BigDecimal> jaDevolvido = new HashMap<>();
        for (VenDevolucao anterior : devolucoes.findByPedidoIdAndEmpresaIdAndStatusInAndDeletedAtIsNull(
                pedidoId, empresaId, List.of("SOLICITADA", "APROVADA", "RECEBIDA")))
            for (VenDevolucaoItem x : itens.findByDevolucaoIdAndEmpresaIdAndDeletedAtIsNull(anterior.getId(), empresaId))
                jaDevolvido.merge(x.getProdutoId(), x.getQuantidade() == null ? BigDecimal.ZERO : x.getQuantidade(), BigDecimal::add);
        Map<Long, BigDecimal> vendidas = new LinkedHashMap<>();
        if (p.getItens() != null) for (var it : p.getItens()) if (it.getDeletedAt() == null && it.getProdutoId() != null)
            vendidas.merge(it.getProdutoId(), it.getQuantidade() == null ? BigDecimal.ZERO : it.getQuantidade(), BigDecimal::add);
        for (var e : itensQtd.entrySet()) {
            BigDecimal saldo = vendidas.getOrDefault(e.getKey(), BigDecimal.ZERO)
                    .subtract(jaDevolvido.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (e.getKey() == null || e.getValue() == null || e.getValue().signum() <= 0
                    || e.getValue().stripTrailingZeros().scale() > 3 || e.getValue().compareTo(saldo) > 0)
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Quantidade invalida, acima do saldo ou com mais de 3 casas decimais");
        }
        VenDevolucao d = new VenDevolucao(); d.setEmpresaId(empresaId); d.setPedidoId(pedidoId);
        if (p.getClienteId() == null) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Pedido sem cliente");
        d.setClienteId(p.getClienteId());
        d.setNumero("DV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        d.setMotivo(motivo); d.setStatus("SOLICITADA"); d = devolucoes.save(d);
        for (var e : itensQtd.entrySet()) {
            VenDevolucaoItem i = new VenDevolucaoItem(); i.setEmpresaId(empresaId); i.setDevolucaoId(d.getId());
            i.setProdutoId(e.getKey()); i.setQuantidade(e.getValue()); i.setQtdRecebida(BigDecimal.ZERO); itens.save(i);
        }
        return d;
    }
    @Transactional public VenDevolucao decidir(Long empresaId, Long userId, Long id, boolean aprovar) {
        VenDevolucao d = exigir(devolucoes.findByIdForUpdate(id, empresaId), "Devolucao inexistente");
        if (!"SOLICITADA".equals(d.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Devolucao ja decidida");
        d.setStatus(aprovar ? "APROVADA" : "REJEITADA");
        d.setDecididaPor(userId);
        d.setDecididaEm(LocalDateTime.now());
        return devolucoes.save(d);
    }
    @Transactional public VenDevolucao receber(Long empresaId, Long id) {
        VenDevolucao d = exigir(devolucoes.findByIdForUpdate(id, empresaId), "Devolucao inexistente");
        if (!"APROVADA".equals(d.getStatus())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Aprove antes de receber");
        Long depId = depositos.findFirstByEmpresaIdAndTipoAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(empresaId, "PADRAO")
                .or(() -> depositos.findFirstByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByIdAsc(empresaId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Nenhum deposito ativo para receber devolucao")).getId();
        depositos.findAtivoForUpdate(depId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Deposito indisponivel"));
        var linhas = new ArrayList<>(itens.findByDevolucaoIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId));
        if (linhas.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Devolucao sem itens");
        for (var i : linhas) {
            if (i.getProdutoId() == null || i.getQuantidade() == null || i.getQuantidade().signum() <= 0
                    || i.getQuantidade().stripTrailingZeros().scale() > 3
                    || (i.getQtdRecebida() != null && i.getQtdRecebida().signum() != 0))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Item de devolucao inconsistente");
        }
        linhas.sort(Comparator.comparing(VenDevolucaoItem::getProdutoId));
        for (VenDevolucaoItem i : linhas) {
            SaldoEstoque s = saldos.findForUpdate(empresaId, depId, i.getProdutoId()).orElseGet(() -> {
                SaldoEstoque n = new SaldoEstoque();
                n.setEmpresaId(empresaId);
                n.setDepositoId(depId);
                n.setProdutoId(i.getProdutoId());
                n.setQuantidade(BigDecimal.ZERO);
                return n; });
            s.setQuantidade((s.getQuantidade() == null ? BigDecimal.ZERO : s.getQuantidade()).add(i.getQuantidade()));
            saldos.save(s);
            MovimentacaoEstoque m = new MovimentacaoEstoque();
            m.setEmpresaId(empresaId);
            m.setProdutoId(i.getProdutoId());
            m.setDepositoId(depId);
            m.setTipo("ENTRADA");
            m.setOrigem("DEVOLUCAO");
            m.setOrigemId(id);
            m.setQuantidade(i.getQuantidade());
            m.setSaldoApos(s.getQuantidade());
            m.setObservacao("Devolucao de venda #" + d.getPedidoId());
            movimentacoes.save(m);
            i.setQtdRecebida(i.getQuantidade());
            itens.save(i);
        }
        integrarFinanceiro(empresaId, d, linhas);
        comissaoService.estornarPorPedido(empresaId, d.getPedidoId(), "Devolucao " + d.getNumero());
        d.setStatus("RECEBIDA");
        d.setRecebidaEm(LocalDateTime.now());
        return devolucoes.save(d);
    }

    /**
     * Encadeamento automatico devolucao -> financeiro (venda).
     * Calcula o valor devolvido proporcional aos itens do pedido (valorTotal
     * por produto, rateando desconto da linha) e:
     * - titulo ABERTO/PARCIAL com saldo: cria Baixa de ajuste sem conta
     *   bancaria (sem movimentacao de caixa), via TituloService.baixar, que
     *   mantem a mesma trava pessimista do titulo e rateia as parcelas;
     * - titulo BAIXADO/saldo zerado: cria titulo P (a restituir ao cliente);
     * - sem titulo ou titulo CANCELADO: so registra o fluxo, sem financeiro.
     * Idempotente: confere Baixa/titulo de restituicao ja existentes pelo
     * marcador antes de criar. Nao emite documento fiscal nem contabiliza:
     * o titulo/Baixa gerados seguem o caminho existente (gerarDeTitulo) e
     * a NF de devolucao permanece pendencia explicita (Fiscal por ultimo).
     */
    private void integrarFinanceiro(Long empresaId, VenDevolucao d, java.util.List<VenDevolucaoItem> linhas) {
        var pedidoOpt = pedidos.findByIdForUpdateAndEmpresaId(d.getPedidoId(), empresaId);
        if (pedidoOpt.isEmpty()) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "PEDIDO_VENDA", d.getPedidoId(), null,
                    "RECEBIDA_SEM_PEDIDO");
            return;
        }
        PedidoVenda pedido = pedidoOpt.get();
        BigDecimal valorDevolvido = calcularValorDevolvido(pedido, linhas);
        if (pedido.getTituloId() == null) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "PEDIDO_VENDA", pedido.getId(), pedido.getNumero(),
                    "RECEBIDA_SEM_TITULO");
            return;
        }
        String marcador = "DEVOLUCAO_VENDA#" + d.getId();
        Titulo titulo = tituloRepository.findForUpdate(pedido.getTituloId(), empresaId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Titulo do pedido inexistente"));
        if ("CANCELADO".equalsIgnoreCase(titulo.getStatus())) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                    "RECEBIDA_TITULO_CANCELADO");
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
                        marcador + " " + (d.getNumero() == null ? "" : d.getNumero())));
            }
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                    "AJUSTE_DEVOLUCAO");
            ajustarContabil(empresaId, d, titulo, titulo.getId(), valorBaixa);
            BigDecimal restante = valorDevolvido.subtract(valorBaixa);
            if (restante.signum() > 0) {
                criarTituloRestituicao(empresaId, d, pedido, titulo, restante, marcador);
            }
            return;
        }
        if (valorDevolvido.signum() > 0) {
            criarTituloRestituicao(empresaId, d, pedido, titulo, valorDevolvido, marcador);
            return;
        }
        documentoFluxoService.ligar(empresaId, null,
                "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                "TITULO", titulo.getId(), titulo.getNumeroDocumento(),
                "RECEBIDA_SEM_AJUSTE");
    }

    private void criarTituloRestituicao(Long empresaId, VenDevolucao d, PedidoVenda pedido, Titulo origem, BigDecimal valor, String marcador) {
        String numeroDoc = "REST-" + (d.getNumero() == null ? ("DV" + d.getId()) : d.getNumero());
        boolean jaExiste = !tituloRepository.findByEmpresaIdAndNumeroDocumentoAndDeletedAtIsNull(empresaId, numeroDoc).isEmpty();
        if (jaExiste) {
            return;
        }
        Titulo r = new Titulo();
        r.setEmpresaId(empresaId);
        r.setTipo("P");
        r.setNumeroDocumento(numeroDoc);
        r.setDescricao("Restituicao devolucao " + (d.getNumero() == null ? ("#" + d.getId()) : d.getNumero())
                + " pedido " + (pedido.getNumero() == null ? ("#" + pedido.getId()) : pedido.getNumero()));
        r.setPessoaId(origem.getPessoaId());
        r.setValorOriginal(valor.setScale(2, RoundingMode.HALF_UP));
        r.setValorSaldo(valor.setScale(2, RoundingMode.HALF_UP));
        r.setDataEmissao(LocalDate.now());
        r.setDataVencimento(LocalDate.now().plusDays(30));
        r.setStatus("ABERTO");
        Titulo salvo = tituloRepository.save(r);
        documentoFluxoService.ligar(empresaId, null,
                "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                "TITULO", salvo.getId(), salvo.getNumeroDocumento(),
                "RESTITUICAO_DEVOLUCAO");
        ajustarContabil(empresaId, d, origem, salvo.getId(), valor);
    }

    /**
     * Ajuste contabil do valor devolvido: espelha proporcionalmente os
     * lancamentos do titulo de origem para o titulo destino. Periodo
     * fechado ou origem nunca contabilizada viram pendencia explicita
     * na trilha, sem travar o recebimento fisico nem o financeiro.
     */
    private void ajustarContabil(Long empresaId, VenDevolucao d,
            Titulo origem, Long tituloDestinoId, BigDecimal valorAjuste) {
        BigDecimal original = origem.getValorOriginal();
        if (original == null || original.signum() <= 0 || valorAjuste == null || valorAjuste.signum() <= 0) {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "TITULO", tituloDestinoId, "SEM_VALOR_ORIGINAL",
                    "AJUSTE_CONTABIL_PENDENTE");
            return;
        }
        BigDecimal proporcao = valorAjuste.divide(original, 10, RoundingMode.HALF_UP);
        if (proporcao.compareTo(BigDecimal.ONE) > 0) proporcao = BigDecimal.ONE;
        var espelho = contabilidadeService.espelharAjusteDevolucao(empresaId, origem.getId(), tituloDestinoId,
                proporcao, "Ajuste devolucao " + (d.getNumero() == null ? ("#" + d.getId()) : d.getNumero()));
        if ("APLICADO".equals(espelho.situacao())) {
            for (var lancado : espelho.lancamentos()) {
                documentoFluxoService.ligar(empresaId, null,
                        "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                        "TITULO", tituloDestinoId, lancado.getId() + ":" + lancado.getPeriodo(),
                        "AJUSTE_CONTABIL");
            }
        } else {
            documentoFluxoService.ligar(empresaId, null,
                    "DEVOLUCAO_VENDA", d.getId(), d.getNumero(),
                    "TITULO", tituloDestinoId, espelho.situacao(),
                    "AJUSTE_CONTABIL_PENDENTE");
        }
    }

    /**
     * Valor devolvido proporcional: por produto, (qtdDevolvida / qtdVendida)
     * * valorTotalVendido do produto, com HALF_UP em 2 casas. Rateia
     * desconto da linha sem inventar regra de restituicao.
     */
    private BigDecimal calcularValorDevolvido(PedidoVenda pedido, java.util.List<VenDevolucaoItem> linhas) {
        Map<Long, BigDecimal> qtdVendida = new HashMap<>();
        Map<Long, BigDecimal> valorVendido = new HashMap<>();
        if (pedido.getItens() != null) for (var it : pedido.getItens()) {
            if (it.getDeletedAt() != null || it.getProdutoId() == null) continue;
            BigDecimal q = it.getQuantidade() == null ? BigDecimal.ZERO : it.getQuantidade();
            BigDecimal v = it.getValorTotal() == null ? BigDecimal.ZERO : it.getValorTotal();
            qtdVendida.merge(it.getProdutoId(), q, BigDecimal::add);
            valorVendido.merge(it.getProdutoId(), v, BigDecimal::add);
        }
        Map<Long, BigDecimal> qtdDev = new HashMap<>();
        for (var i : linhas) {
            if (i.getProdutoId() == null || i.getQuantidade() == null) continue;
            qtdDev.merge(i.getProdutoId(), i.getQuantidade(), BigDecimal::add);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var e : qtdDev.entrySet()) {
            BigDecimal vendida = qtdVendida.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal valor = valorVendido.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (vendida.signum() <= 0 || e.getValue().signum() <= 0) continue;
            BigDecimal proporcao = e.getValue().divide(vendida, 10, RoundingMode.HALF_UP);
            if (proporcao.compareTo(BigDecimal.ONE) > 0) proporcao = BigDecimal.ONE;
            total = total.add(valor.multiply(proporcao));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
