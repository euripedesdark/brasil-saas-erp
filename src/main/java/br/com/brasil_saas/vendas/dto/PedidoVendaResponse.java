package br.com.brasil_saas.vendas.dto;

import br.com.brasil_saas.vendas.model.ItemPedidoVenda;
import br.com.brasil_saas.vendas.model.PedidoVenda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta do pedido de venda.
 *
 * <p>Por que os cinco campos de dinheiro em vez de so {@code valorTotal}: o
 * PDV mostra subtotal, desconto e total, e com so o total a tela nao consegue
 * explicar a conta ao cliente. Medido no payload que gerava divergencia:
 *
 * <pre>
 *   {
 *     "valorProdutos":       55.80,
 *     "valorDescontoItens":   5.58,
 *     "valorDescontoPedido":  0.00,
 *     "valorDescontoTotal":   5.58,
 *     "valorTotal":          50.22
 *   }
 * </pre>
 *
 * <p><b>Como os tres descontos se relacionam:</b>
 * {@code valorDescontoTotal} e' a soma de {@code valorDescontoItens} +
 * {@code valorDescontoPedido}, e o {@code valorTotal} e'
 * {@code valorProdutos} menos {@code valorDescontoTotal}, mais o frete. Antes
 * destes campos, {@code valorDesconto} no pedido guardava SO o desconto do
 * pedido, com o do item ja deduzido dentro do item, e o total da tela nao
 * batia com o do banco.
 *
 * <p>Regra de precedencia, em uma linha: o desconto do item e' aplicado
 * primeiro, sobre a linha; o desconto do pedido incide sobre o que sobrou.
 * {@code percentualDesconto} e {@code valorDesconto} do pedido sao mutuamente
 * exclusivos — viram os dois preenchidos, a criacao e' recusada com HTTP 400.
 */
public record PedidoVendaResponse(
        Long id,
        String numero,
        String tipo,
        String status,
        Long clienteId,
        Long vendedorId,
        Long tabelaPrecoId,
        BigDecimal percentualDesconto,
        String canalVenda,
        String origem,
        LocalDate dataEmissao,
        LocalDate dataEntrega,
        // Soma de (quantidade x valorUnitario), ainda SEM nenhum desconto.
        BigDecimal valorProdutos,
        BigDecimal valorServicos,
        // Soma dos descontos dados linha a linha.
        BigDecimal valorDescontoItens,
        // Desconto dado no pedido, por valor ou por percentual. Nunca os dois.
        BigDecimal valorDescontoPedido,
        // valorDescontoItens + valorDescontoPedido
        BigDecimal valorDescontoTotal,
        BigDecimal valorFrete,
        // valorProdutos + valorServicos - valorDescontoTotal + valorFrete
        BigDecimal valorTotal,
        Long tituloId,
        String observacao,
        List<Item> itens) {

    public static PedidoVendaResponse from(PedidoVenda pedido) {
        BigDecimal descontoItens = zeroSeNulo(pedido.getItens() == null ? null
                : pedido.getItens().stream()
                        .map(ItemPedidoVenda::getValorDesconto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal descontoTotal = zeroSeNulo(pedido.getValorDesconto());
        // A coluna do pedido guarda o desconto TOTAL. O do pedido isolado e' o
        // que sobra depois de tirar o dos itens.
        BigDecimal descontoPedido = descontoTotal.subtract(descontoItens);

        return new PedidoVendaResponse(
                pedido.getId(), pedido.getNumero(), pedido.getTipo(), pedido.getStatus(),
                pedido.getClienteId(), pedido.getVendedorId(), pedido.getTabelaPrecoId(),
                pedido.getPercentualDesconto(), pedido.getCanalVenda(), pedido.getOrigem(),
                pedido.getDataEmissao(), pedido.getDataEntrega(),
                zeroSeNulo(pedido.getValorProdutos()),
                zeroSeNulo(pedido.getValorServicos()),
                descontoItens,
                descontoPedido,
                descontoTotal,
                zeroSeNulo(pedido.getValorFrete()),
                zeroSeNulo(pedido.getValorTotal()),
                pedido.getTituloId(), pedido.getObservacao(),
                pedido.getItens() == null ? List.of()
                        : pedido.getItens().stream().map(Item::from).toList());
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    public record Item(Long id, Integer numeroItem, Long produtoId, Long servicoId, String descricao,
                       BigDecimal quantidade, String unidade, BigDecimal valorUnitario,
                       BigDecimal valorDesconto, BigDecimal valorTotal) {
        static Item from(ItemPedidoVenda item) {
            return new Item(item.getId(), item.getNumeroItem(), item.getProdutoId(), item.getServicoId(),
                    item.getDescricao(), item.getQuantidade(), item.getUnidade(), item.getValorUnitario(),
                    zeroSeNulo(item.getValorDesconto()), zeroSeNulo(item.getValorTotal()));
        }
    }
}
