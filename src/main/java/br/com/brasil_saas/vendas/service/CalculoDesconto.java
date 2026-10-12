package br.com.brasil_saas.vendas.service;

import br.com.brasil_saas.shared.exception.BusinessException;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Regra de desconto do pedido de venda, no unico lugar.
 *
 * <h2>O PROBLEMA, medido</h2>
 * A conta vivia espalhada dentro de {@code PedidoVendaServiceImpl.criar} e
 * descontaba o desconto do item DUAS vezes:
 *
 * <pre>
 *   item.valorTotal   = quantidade * valorUnitario - descontoItem
 *   totalProdutos    += item.valorTotal                   (ja descontado)
 *   pedido.valorTotal = totalProdutos - pedido.valorDesconto + frete
 * </pre>
 *
 * Postando {@code valorDesconto = 5.58} no item E no pedido, a API respondia:
 *
 * <pre>
 *   item.valorTotal   =  50,22   (2 x 27,90 - 5,58)
 *   pedido.valorTotal =  44,64   (50,22 - 5,58, o mesmo 5,58 outra vez)
 * </pre>
 *
 * Alem disso o pedido tinha TRES campos de desconto concorrentes
 * ({@code Item.valorDesconto}, {@code Pedido.valorDesconto},
 * {@code Pedido.percentualDesconto}) sem nenhuma regra dizendo qual manda.
 * Num PDV, onde o caixa desconta a linha e a tela tambem aceita desconto no
 * total, isso vira divergencia financeira.
 *
 * <h2>A REGRA</h2>
 * <ol>
 *   <li>Cada item desconta o seu desconto, uma unica vez.</li>
 *   <li>O desconto do pedido incide sobre o subtotal JA liquido dos itens.</li>
 *   <li>{@code percentualDesconto} e {@code valorDesconto} sao mutuamente
 *       exclusivos. Se os dois vierem preenchidos, o pedido e' recusado.</li>
 * </ol>
 *
 * <pre>
 *   valorProdutos       = soma( quantidade * valorUnitario )       // bruto
 *   valorDescontoItens  = soma( desconto do item )
 *   baseDescontoPedido  = valorProdutos - valorDescontoItens
 *   valorDescontoPedido = valorDesconto, OU percentual/100 * base
 *   valorDescontoTotal  = valorDescontoItens + valorDescontoPedido
 *   valorTotal          = valorProdutos - valorDescontoTotal + frete
 * </pre>
 *
 * Exemplo do enunciado, com o mesmo payload que gerava a divergencia:
 *
 * <pre>
 *   valorProdutos: 55,80   valorDescontoItens: 5,58   valorDescontoPedido: 0,00
 *   valorDescontoTotal: 5,58    valorTotal: 50,22
 * </pre>
 *
 * <h2>POR QUE UMA CLASSE E NAO UM METODO</h2>
 * A conta e' chamada de tres lugares — criar, e os dois pontos onde um pedido ja
 * gravado e' repricotado — e o resultado precisa ser identico nos tres. Se
 * ficasse dentro do metodo, o proximo devolve mudaria a formula de novo, que e'
 * exatamente como o bug nasceu.
 */
@Getter
public final class CalculoDesconto {

    private final BigDecimal valorProdutos;
    private final BigDecimal valorServicos;
    private final BigDecimal valorDescontoItens;
    private final BigDecimal valorDescontoPedido;
    private final BigDecimal valorDescontoTotal;
    private final BigDecimal valorFrete;
    private final BigDecimal valorTotal;
    private final BigDecimal percentualDesconto;

    private CalculoDesconto(BigDecimal valorProdutos, BigDecimal valorServicos,
                            BigDecimal valorDescontoItens, BigDecimal valorDescontoPedido,
                            BigDecimal percentualDesconto, BigDecimal valorFrete) {
        this.valorProdutos = valorProdutos;
        this.valorServicos = valorServicos;
        this.valorDescontoItens = valorDescontoItens;
        this.valorDescontoPedido = valorDescontoPedido;
        this.valorDescontoTotal = valorDescontoItens.add(valorDescontoPedido);
        this.percentualDesconto = percentualDesconto;
        this.valorFrete = valorFrete;
        this.valorTotal = valorProdutos.add(valorServicos)
                .subtract(this.valorDescontoTotal)
                .add(valorFrete);
    }

    /**
     * Item cru: quantidade, preco e o desconto que o caixa deu NA LINHA.
     * O desconto do item ja vem deduzido aqui, uma vez so.
     */
    public record Item(BigDecimal quantidade, BigDecimal valorUnitario, BigDecimal valorDesconto) {
        public Item {
            quantidade = quantidade == null ? BigDecimal.ZERO : quantidade;
            valorUnitario = valorUnitario == null ? BigDecimal.ZERO : valorUnitario;
            valorDesconto = valorDesconto == null ? BigDecimal.ZERO : valorDesconto;
        }

        public BigDecimal bruto() {
            return quantidade.multiply(valorUnitario);
        }

        public BigDecimal liquido() {
            return bruto().subtract(valorDesconto);
        }
    }

    /**
     * percentualDesconto e valorDesconto sao exclusivos: se os dois vierem
     * preenchidos, o pedido e' recusado em vez de escolher um silenciosamente.
     */
    public static CalculoDesconto de(List<Item> itens, BigDecimal percentualDesconto,
                                     BigDecimal valorDesconto, BigDecimal valorFrete) {
        percentualDesconto = percentualDesconto == null ? BigDecimal.ZERO : percentualDesconto;
        valorDesconto = valorDesconto == null ? BigDecimal.ZERO : valorDesconto;
        valorFrete = valorFrete == null ? BigDecimal.ZERO : valorFrete;

        if (percentualDesconto.signum() > 0 && valorDesconto.signum() > 0) {
            // IllegalArgumentException, e' nao BusinessException: os dois campos
            // preenchidos e' entrada invalida, e o dono pediu 400. BusinessException
            // vira 422, que e' para regra de negocio (saldo insuficiente, status
            // invalido). O handler global ja mapeia IllegalArgumentException
            // para 400 DADOS_INVALIDOS.
            throw new IllegalArgumentException(
                    "Informe percentualDesconto OU valorDesconto, nunca os dois: "
                            + "com os dois preenchidos nao existe ordem de precedencia");
        }
        if (percentualDesconto.signum() < 0 || valorDesconto.signum() < 0) {
            throw new BusinessException("Desconto do pedido nao pode ser negativo");
        }
        if (percentualDesconto.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Percentual de desconto nao pode exceder 100");
        }
        if (valorFrete.signum() < 0) {
            throw new BusinessException("Frete nao pode ser negativo");
        }

        BigDecimal bruto = BigDecimal.ZERO;
        BigDecimal descontoItens = BigDecimal.ZERO;
        for (Item item : itens == null ? List.<Item>of() : itens) {
            if (item.quantidade().signum() <= 0 || item.valorUnitario().signum() < 0) {
                throw new BusinessException("Item deve possuir quantidade positiva e valor unitario nao negativo");
            }
            if (item.valorDesconto().signum() < 0 || item.valorDesconto().compareTo(item.bruto()) > 0) {
                throw new BusinessException("Desconto do item deve estar entre zero e o valor bruto do item");
            }
            bruto = bruto.add(item.bruto());
            descontoItens = descontoItens.add(item.valorDesconto());
        }
        if (descontoItens.signum() < 0) {
            throw new BusinessException("Desconto do item nao pode ser negativo");
        }

        BigDecimal base = bruto.subtract(descontoItens);
        BigDecimal descontoPedido = percentualDesconto.signum() > 0
                ? base.multiply(percentualDesconto)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : valorDesconto;

        // Desconto maior que o proprio pedido deixa o total negativo, que nao
        // tem leitura em nota nem em extrato.
        if (descontoPedido.compareTo(base) > 0) {
            throw new BusinessException("Desconto do pedido (" + descontoPedido
                    + ") e' maior que o subtotal liquido dos itens (" + base + ")");
        }

        return new CalculoDesconto(bruto, BigDecimal.ZERO, descontoItens, descontoPedido,
                percentualDesconto, valorFrete);
    }

    public BigDecimal subtotalLiquido() {
        return valorProdutos.subtract(valorDescontoItens);
    }
}
