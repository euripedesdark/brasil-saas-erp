package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.IntercompanyLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IntercompanyLancamentoRepository extends JpaRepository<IntercompanyLancamento, Long> {

    Optional<IntercompanyLancamento> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);

    @Query("SELECT i FROM IntercompanyLancamento i WHERE i.empresaId = :parceira "
            + "AND i.empresaParceiraId = :empresaId AND i.numero = :numero AND i.deletedAt IS NULL "
            + "AND (i.competencia = :competencia OR (i.competencia IS NULL AND :competencia IS NULL))")
    List<IntercompanyLancamento> findEspelho(@Param("parceira") Long parceira,
            @Param("empresaId") Long empresaId, @Param("numero") String numero,
            @Param("competencia") LocalDate competencia);

    List<IntercompanyLancamento> findByEmpresaIdAndReconciliadoTrueAndDeletedAtIsNullAndCompetenciaGreaterThanEqualAndCompetenciaLessThanOrderByNumero(
            Long empresaId, LocalDate de, LocalDate ate);
}
