package br.com.brasil_saas.financeiro.repository;
import br.com.brasil_saas.financeiro.model.Titulo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface TituloRepository extends JpaRepository<Titulo, Long> {
    List<Titulo> findByEmpresaIdAndDeletedAtIsNullOrderByDataVencimento(Long empresaId);
    List<Titulo> findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(Long empresaId, Long pessoaId);
    List<Titulo> findByEmpresaIdAndStatusAndDeletedAtIsNullOrderByDataVencimento(Long empresaId, String status);
    Optional<Titulo> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<Titulo> findByEmpresaIdAndNumeroDocumentoAndDeletedAtIsNull(Long empresaId, String numeroDocumento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Titulo t where t.id = :id and t.empresaId = :empresaId and t.deletedAt is null")
    Optional<Titulo> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("SELECT t.numeroDocumento AS numeroDocumento, t.dataEmissao AS dataEmissao, "
            + "t.dataVencimento AS dataVencimento, t.descricao AS descricao, t.tipo AS tipo, "
            + "t.valorOriginal AS valorOriginal, t.valorSaldo AS valorSaldo, t.status AS status "
            + "FROM Titulo t WHERE t.empresaId = :empresaId AND t.deletedAt IS NULL "
            + "AND (:inicio IS NULL OR t.dataEmissao >= :inicio) "
            + "AND (:fim IS NULL OR t.dataEmissao <= :fim) "
            + "ORDER BY t.dataEmissao DESC")
    java.util.List<FinanceiroRow> relatorioFinanceiro(@Param("empresaId") Long empresaId,
            @Param("inicio") java.time.LocalDate inicio, @Param("fim") java.time.LocalDate fim,
            Pageable pageable);

    interface FinanceiroRow {
        String getNumeroDocumento();
        java.time.LocalDate getDataEmissao();
        java.time.LocalDate getDataVencimento();
        String getDescricao();
        String getTipo();
        java.math.BigDecimal getValorOriginal();
        java.math.BigDecimal getValorSaldo();
        String getStatus();
    }
}
