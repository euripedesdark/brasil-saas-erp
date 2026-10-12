package br.com.brasil_saas.vendas.service;

import br.com.brasil_saas.estoque.repository.DepositoRepository;
import br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.vendas.model.ItemPedidoVenda;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AtpService {

    private final PedidoVendaRepository pedidoRepository;
    private final DepositoRepository depositoRepository;
    private final SaldoEstoqueRepository saldoEstoqueRepository;
    private final ReservaEstoqueRepository reservaEstoqueRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> verificar(Long empresaId, Long pedidoId) {
        PedidoVenda pedido = pedidoRepository.findByIdAndEmpresaId(pedidoId, empresaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de venda nao encontrado"));
        Long depositoId = depositoRepository.findByEmpresaIdAndCodigoAndAtivoTrue(empresaId, "PADRAO")
                .map(br.com.brasil_saas.estoque.model.Deposito::getId)
                .orElse(null);

        List<Map<String, Object>> linhas = new ArrayList<>();
        boolean ok = true;
        if (pedido.getItens() != null) {
            for (ItemPedidoVenda item : pedido.getItens()) {
                if (item.getProdutoId() == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("produtoId", item.getProdutoId());
                row.put("descricao", item.getDescricao());
                row.put("solicitado", item.getQuantidade());
                BigDecimal saldo = BigDecimal.ZERO;
                BigDecimal reservado = BigDecimal.ZERO;
                BigDecimal disponivel = BigDecimal.ZERO;
                if (depositoId != null) {
                    saldo = saldoEstoqueRepository
                            .findByEmpresaIdAndDepositoIdAndProdutoId(empresaId, depositoId, item.getProdutoId())
                            .map(s -> s.getQuantidade() == null ? BigDecimal.ZERO : s.getQuantidade())
                            .orElse(BigDecimal.ZERO);
                    BigDecimal r = reservaEstoqueRepository.sumAtivas(empresaId, depositoId, item.getProdutoId());
                    reservado = r == null ? BigDecimal.ZERO : r;
                    disponivel = saldo.subtract(reservado);
                }
                row.put("saldo", saldo);
                row.put("reservado", reservado);
                row.put("disponivel", disponivel);
                boolean itemOk = disponivel.compareTo(item.getQuantidade() == null ? BigDecimal.ZERO : item.getQuantidade()) >= 0;
                row.put("ok", itemOk);
                if (!itemOk) ok = false;
                linhas.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pedidoId", pedido.getId());
        out.put("numero", pedido.getNumero());
        out.put("depositoId", depositoId);
        out.put("disponivelTotal", ok);
        out.put("itens", linhas);
        return out;
    }
}
