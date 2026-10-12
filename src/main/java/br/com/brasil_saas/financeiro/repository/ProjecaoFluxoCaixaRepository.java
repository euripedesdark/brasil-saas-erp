package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.ProjecaoFluxoCaixa;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjecaoFluxoCaixaRepository extends JpaRepository<ProjecaoFluxoCaixa, Long> {
    @Query("SELECT f.dataProjecao AS dataProjecao, "
            + "f.valorPrevistoEntrada AS valorPrevistoEntrada, "
            + "f.valorPrevistoSaida AS valorPrevistoSaida, "
            + "(COALESCE(f.valorPrevistoEntrada, 0) - COALESCE(f.valorPrevistoSaida, 0)) AS saldoProjetado "
            + "FROM ProjecaoFluxoCaixa f WHERE f.empresaId = :empresaId AND f.deletedAt IS NULL "
            + "AND (:inicio IS NULL OR f.dataProjecao >= :inicio) "
            + "AND (:fim IS NULL OR f.dataProjecao <= :fim) "
            + "ORDER BY f.dataProjecao DESC")
    List<FluxoRow> relatorioFluxoCaixa(@Param("empresaId") Long empresaId,
            @Param("inicio") java.time.LocalDate inicio, @Param("fim") java.time.LocalDate fim,
            Pageable pageable);

    interface FluxoRow {
        java.time.LocalDate getDataProjecao();
        java.math.BigDecimal getValorPrevistoEntrada();
        java.math.BigDecimal getValorPrevistoSaida();
        java.math.BigDecimal getSaldoProjetado();
    }
}
