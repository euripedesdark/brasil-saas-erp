package br.com.brasil_saas.compras.repository;

import br.com.brasil_saas.compras.model.ItemPedidoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemPedidoCompraRepository extends JpaRepository<ItemPedidoCompra, Long> {

    @Query("SELECT i.produtoId AS produtoId, SUM(i.quantidadeRecebida * i.valorUnitario) AS somaValor, SUM(i.quantidadeRecebida) AS somaQtd " +
           "FROM ItemPedidoCompra i JOIN i.pedido p " +
           "WHERE i.empresaId = :empresaId AND i.produtoId IS NOT NULL AND i.quantidadeRecebida > 0 " +
           "AND p.status <> 'CANCELADO' AND p.deletedAt IS NULL AND i.deletedAt IS NULL " +
           "GROUP BY i.produtoId")
    List<MediaCompra> mediasPonderadas(@Param("empresaId") Long empresaId);

    interface MediaCompra {
        Long getProdutoId();
        java.math.BigDecimal getSomaValor();
        java.math.BigDecimal getSomaQtd();
    }
    @Query("SELECT p.dataPrevisaoEntrega AS dataEntrega, "
            + "SUM(i.quantidade - i.quantidadeRecebida) AS saldo "
            + "FROM ItemPedidoCompra i JOIN i.pedido p "
            + "WHERE p.empresaId = :empresaId AND i.produtoId = :produtoId "
            + "AND p.deletedAt IS NULL AND i.deletedAt IS NULL "
            + "AND p.status IN ('ABERTO', 'PARCIAL') AND p.dataPrevisaoEntrega IS NOT NULL "
            + "GROUP BY p.dataPrevisaoEntrega HAVING SUM(i.quantidade - i.quantidadeRecebida) > 0")
    List<EntradaPrevista> entradasFuturas(@Param("empresaId") Long empresaId,
            @Param("produtoId") Long produtoId);

    interface EntradaPrevista {
        java.time.LocalDate getDataEntrega();
        java.math.BigDecimal getSaldo();
    }
}
