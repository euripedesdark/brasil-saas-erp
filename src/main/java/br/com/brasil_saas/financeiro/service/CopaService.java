package br.com.brasil_saas.financeiro.service;

import br.com.brasil_saas.cadastro.model.Produto;
import br.com.brasil_saas.cadastro.repository.ProdutoRepository;
import br.com.brasil_saas.vendas.model.ItemPedidoVenda;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.compras.repository.ItemPedidoCompraRepository;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CO-PA (Controlling – Profitability Analysis): receita de pedidos FATURADOS por cliente e por mês.
 * O custo vem, por item, do custo médio ponderado das compras recebidas; na falta dele, do preço de custo do
 * cadastro; só então do percentual estimado. A resposta informa a cobertura de custo real.
 */
@Service
@RequiredArgsConstructor
public class CopaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final PedidoVendaRepository pedidoVendaRepository;
    private final ProdutoRepository produtoRepository;
    private final ItemPedidoCompraRepository itemPedidoCompraRepository;

    /** Acumulador de receita e custo, separando o que veio de fonte real. */
    private static final class Acum {
        BigDecimal receita = BigDecimal.ZERO, custo = BigDecimal.ZERO, receitaReal = BigDecimal.ZERO;
        BigDecimal cmvReal = BigDecimal.ZERO, cmvEstimado = BigDecimal.ZERO;
        int pedidos;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> porCliente(Long empresaId, LocalDate de, LocalDate ate) {
        List<PedidoVenda> pedidos = faturados(empresaId).stream()
                .filter(p -> (de == null || !p.getDataEmissao().isBefore(de)))
                .filter(p -> (ate == null || !p.getDataEmissao().isAfter(ate)))
                .filter(p -> p.getClienteId() != null).toList();
        Custos custos = carregarCustos(empresaId, pedidos);

        Map<Long, Acum> porCli = new LinkedHashMap<>();
        Acum total = new Acum();
        for (PedidoVenda p : pedidos) {
            acumular(porCli.computeIfAbsent(p.getClienteId(), k -> new Acum()), p, custos);
            acumular(total, p, custos);
        }
        List<Map<String, Object>> linhas = new ArrayList<>();
        porCli.forEach((cli, a) -> {
            Map<String, Object> row = linha(a);
            row.put("clienteId", cli);
            row.put("qtdePedidos", a.pedidos);
            linhas.add(0, row);
        });
        linhas.sort((x, y) -> ((BigDecimal) y.get("margem")).compareTo((BigDecimal) x.get("margem")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("de", de);
        out.put("ate", ate);
        out.put("totalReceita", total.receita);
        out.put("totalCustoEstimado", total.custo);
        out.put("totalCmvReal", total.cmvReal);
        out.put("totalCmvEstimado", total.cmvEstimado);
        out.put("totalMargem", total.receita.subtract(total.custo));
        out.put("coberturaCmvPct", CmvCalculadora.cobertura(total.receitaReal, total.receita));
        out.put("clientes", linhas);
        out.put("aviso", aviso(total));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> porPeriodo(Long empresaId, int ano) {
        List<PedidoVenda> pedidos = faturados(empresaId).stream()
                .filter(p -> p.getDataEmissao().getYear() == ano).toList();
        Custos custos = carregarCustos(empresaId, pedidos);

        Map<String, Acum> porMes = new TreeMap<>();
        Acum total = new Acum();
        for (PedidoVenda p : pedidos) {
            String mes = String.format("%04d-%02d", p.getDataEmissao().getYear(), p.getDataEmissao().getMonthValue());
            acumular(porMes.computeIfAbsent(mes, k -> new Acum()), p, custos);
            acumular(total, p, custos);
        }
        List<Map<String, Object>> meses = new ArrayList<>();
        porMes.forEach((mes, a) -> {
            Map<String, Object> m = linha(a);
            m.put("periodo", mes);
            meses.add(m);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ano", ano);
        out.put("coberturaCmvPct", CmvCalculadora.cobertura(total.receitaReal, total.receita));
        out.put("meses", meses);
        out.put("aviso", aviso(total));
        return out;
    }

    private List<PedidoVenda> faturados(Long empresaId) {
        return pedidoVendaRepository.findByEmpresaIdOrderByDataEmissaoDesc(empresaId).stream()
                .filter(p -> "FATURADO".equals(p.getStatus()) && p.getDataEmissao() != null).toList();
    }

    private record Custos(Map<Long, BigDecimal> medioCompras, Map<Long, BigDecimal> cadastro) {}

    private Custos carregarCustos(Long empresaId, List<PedidoVenda> pedidos) {
        Set<Long> ids = pedidos.stream().flatMap(p -> p.getItens().stream())
                .map(ItemPedidoVenda::getProdutoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, BigDecimal> medio = new HashMap<>();
        Map<Long, BigDecimal> cadastro = new HashMap<>();
        if (ids.isEmpty()) return new Custos(medio, cadastro);
        for (ItemPedidoCompraRepository.MediaCompra m : itemPedidoCompraRepository.mediasPonderadas(empresaId)) {
            if (m.getProdutoId() == null || !ids.contains(m.getProdutoId())) continue;
            if (m.getSomaQtd() == null || m.getSomaQtd().signum() == 0 || m.getSomaValor() == null) continue;
            medio.put(m.getProdutoId(), m.getSomaValor().divide(m.getSomaQtd(), 4, RoundingMode.HALF_UP));
        }
        for (Produto pr : produtoRepository.findAllById(ids)) cadastro.put(pr.getId(), pr.getPrecoCusto());
        return new Custos(medio, cadastro);
    }

    private void acumular(Acum a, PedidoVenda p, Custos c) {
        a.pedidos++;
        if (p.getItens().isEmpty()) {
            BigDecimal rec = nz(p.getValorTotal());
            var custo = CmvCalculadora.custoLinha(null, rec, null, null);
            somar(a, rec, custo);
            return;
        }
        for (ItemPedidoVenda i : p.getItens()) {
            BigDecimal rec = nz(i.getValorTotal());
            var custo = CmvCalculadora.custoLinha(i.getQuantidade(), rec,
                    i.getProdutoId() == null ? null : c.medioCompras().get(i.getProdutoId()),
                    i.getProdutoId() == null ? null : c.cadastro().get(i.getProdutoId()));
            somar(a, rec, custo);
        }
    }

    private void somar(Acum a, BigDecimal receita, CmvCalculadora.Custo custo) {
        a.receita = a.receita.add(receita);
        a.custo = a.custo.add(custo.valor());
        if (custo.real()) { a.cmvReal = a.cmvReal.add(custo.valor()); a.receitaReal = a.receitaReal.add(receita); }
        else a.cmvEstimado = a.cmvEstimado.add(custo.valor());
    }

    private Map<String, Object> linha(Acum a) {
        BigDecimal margem = a.receita.subtract(a.custo);
        BigDecimal pct = a.receita.signum() == 0 ? BigDecimal.ZERO
                : margem.multiply(CEM).divide(a.receita, 2, RoundingMode.HALF_UP);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("receita", a.receita);
        row.put("custoEstimado", a.custo);
        row.put("cmvReal", a.cmvReal);
        row.put("cmvEstimado", a.cmvEstimado);
        row.put("margem", margem);
        row.put("margemPct", pct);
        row.put("coberturaCmvPct", CmvCalculadora.cobertura(a.receitaReal, a.receita));
        return row;
    }

    private String aviso(Acum t) {
        return t.cmvEstimado.signum() == 0 ? "Custo 100% de fonte real (média das compras recebidas ou custo de cadastro)."
                : "Parte da receita usa custo estimado (70%) por falta de custo de compra/cadastro; veja coberturaCmvPct.";
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
