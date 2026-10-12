package br.com.brasil_saas.estoque.repository;

import br.com.brasil_saas.estoque.model.MovimentacaoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {
    List<MovimentacaoEstoque> findByEmpresaIdAndProdutoIdAndDeletedAtIsNullOrderByDataMovimentoDesc(Long empresaId, Long produtoId);
    List<MovimentacaoEstoque> findByEmpresaIdAndDeletedAtIsNullOrderByDataMovimentoDesc(Long empresaId);

    interface EnderecoSaldo {
        Long getEnderecoId();
        Long getLoteId();
        Long getProdutoId();
        BigDecimal getQuantidade();
        java.time.LocalDate getValidade();
    }

    @Query("""
            SELECT m.enderecoId AS enderecoId, m.loteId AS loteId,
                   m.produtoId AS produtoId, SUM(m.quantidade) AS quantidade
            FROM MovimentacaoEstoque m
            WHERE m.empresaId = :empresaId AND m.depositoId = :depositoId
              AND m.enderecoId IS NOT NULL AND m.deletedAt IS NULL
              AND (:produtoId IS NULL OR m.produtoId = :produtoId)
              AND (:loteId IS NULL OR m.loteId = :loteId)
            GROUP BY m.enderecoId, m.loteId, m.produtoId
            HAVING SUM(m.quantidade) > 0
            ORDER BY m.enderecoId, m.produtoId, m.loteId
            """)
    List<EnderecoSaldo> saldosPorEnderecoCompleto(@Param("empresaId") Long empresaId,
                                                   @Param("depositoId") Long depositoId,
                                                   @Param("produtoId") Long produtoId,
                                                   @Param("loteId") Long loteId);

    @Query(value = "SELECT m.endereco_id AS enderecoId, m.lote_id AS loteId, m.produto_id AS produtoId, COALESCE(SUM(m.quantidade),0) AS quantidade, MAX(l.data_validade) AS validade " +
            "FROM brasil_saas.bc_est_movimentacao m " +
            "LEFT JOIN brasil_saas.bc_est_lote l ON l.id = m.lote_id AND l.empresa_id = m.empresa_id AND l.deleted_at IS NULL " +
            "WHERE m.empresa_id=:empresaId AND m.deposito_id=:depositoId AND m.produto_id=:produtoId AND m.endereco_id IS NOT NULL AND m.deleted_at IS NULL " +
            "AND (:loteId IS NULL OR m.lote_id=:loteId) " +
            "AND (m.lote_id IS NULL OR l.id IS NULL OR l.data_validade IS NULL OR l.data_validade >= CURRENT_DATE) " +
            "GROUP BY m.endereco_id,m.lote_id,m.produto_id HAVING COALESCE(SUM(m.quantidade),0)>0 " +
            "ORDER BY MAX(l.data_validade) ASC NULLS LAST, m.endereco_id, m.lote_id", nativeQuery = true)
    List<EnderecoSaldo> saldosPorEndereco(@Param("empresaId") Long empresaId,
                                          @Param("depositoId") Long depositoId,
                                          @Param("produtoId") Long produtoId,
                                          @Param("loteId") Long loteId);
}