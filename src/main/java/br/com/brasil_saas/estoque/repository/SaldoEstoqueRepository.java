package br.com.brasil_saas.estoque.repository;

import br.com.brasil_saas.estoque.model.SaldoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SaldoEstoqueRepository extends JpaRepository<SaldoEstoque, Long> {
    @Query("SELECT s FROM SaldoEstoque s JOIN Deposito d ON d.id = s.depositoId WHERE s.empresaId = :empresaId AND s.produtoId = :produtoId AND d.codigo = 'PADRAO' AND d.ativo = true")
    Optional<SaldoEstoque> findByEmpresaIdAndProdutoId(Long empresaId, Long produtoId);

    @Query("SELECT s FROM SaldoEstoque s JOIN Deposito d ON d.id = s.depositoId WHERE s.empresaId = :empresaId AND s.produtoId = :produtoId AND d.codigo = 'PADRAO' AND d.ativo = true")
    Optional<SaldoEstoque> findByEmpresaIdAndProdutoIdAndDeletedAtIsNull(Long empresaId, Long produtoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaldoEstoque s JOIN Deposito d ON d.id = s.depositoId WHERE s.empresaId = :empresaId AND s.produtoId = :produtoId AND d.codigo = 'PADRAO' AND d.ativo = true")
    Optional<SaldoEstoque> findByEmpresaIdAndProdutoIdForUpdate(Long empresaId, Long produtoId);

    @Query("SELECT s FROM SaldoEstoque s WHERE s.empresaId = :empresaId AND s.depositoId = :depositoId AND s.produtoId = :produtoId AND s.deletedAt IS NULL")
    Optional<SaldoEstoque> findByEmpresaIdAndDepositoIdAndProdutoId(Long empresaId, Long depositoId, Long produtoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SaldoEstoque s WHERE s.empresaId = :empresaId AND s.depositoId = :depositoId AND s.produtoId = :produtoId AND s.deletedAt IS NULL")
    Optional<SaldoEstoque> findForUpdate(@Param("empresaId") Long empresaId, @Param("depositoId") Long depositoId, @Param("produtoId") Long produtoId);

    List<SaldoEstoque> findByEmpresaIdOrderByProdutoIdAsc(Long empresaId);
    List<SaldoEstoque> findByEmpresaIdAndDepositoIdOrderByProdutoIdAsc(Long empresaId, Long depositoId);
    @Query("SELECT COALESCE(SUM(s.quantidade), 0) FROM SaldoEstoque s "
            + "WHERE s.empresaId = :empresaId AND s.produtoId = :produtoId AND s.deletedAt IS NULL")
    java.math.BigDecimal somaPorProduto(@Param("empresaId") Long empresaId,
            @Param("produtoId") Long produtoId);

    @Query("SELECT COALESCE(SUM(s.quantidade), 0) FROM SaldoEstoque s "
            + "WHERE s.empresaId = :empresaId AND s.produtoId = :produtoId "
            + "AND s.depositoId = :depositoId AND s.deletedAt IS NULL")
    java.math.BigDecimal somaPorProdutoEDeposito(@Param("empresaId") Long empresaId,
            @Param("produtoId") Long produtoId, @Param("depositoId") Long depositoId);
}
