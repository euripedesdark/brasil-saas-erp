package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.LancamentoPartida;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LancamentoPartidaRepository extends JpaRepository<LancamentoPartida, Long> {
    List<LancamentoPartida> findByLancamentoIdAndDeletedAtIsNull(Long lancamentoId);

    @Query("SELECT lp FROM LancamentoPartida lp JOIN LancamentoContabil l ON l.id = lp.lancamentoId "
            + "WHERE lp.empresaId = :empresaId AND l.empresaId = :empresaId "
            + "AND l.deletedAt IS NULL AND lp.deletedAt IS NULL AND l.periodoContabilId = :periodoId")
    List<LancamentoPartida> partidasDoPeriodo(@Param("empresaId") Long empresaId,
            @Param("periodoId") Long periodoId);

    @Query("SELECT lp, l.periodoContabilId FROM LancamentoPartida lp JOIN LancamentoContabil l "
            + "ON l.id = lp.lancamentoId "
            + "WHERE lp.empresaId = :empresaId AND l.empresaId = :empresaId "
            + "AND l.deletedAt IS NULL AND lp.deletedAt IS NULL "
            + "AND (l.periodoContabilId = :periodoId OR l.periodoContabilId IS NULL)")
    List<Object[]> partidasParaBalancete(@Param("empresaId") Long empresaId,
            @Param("periodoId") Long periodoId);

    @Query("SELECT pc.codigo AS codigo, pc.descricao AS descricao, pc.natureza AS natureza, "
            + "COALESCE(SUM(CASE WHEN lp.tipo = 'D' THEN lp.valor ELSE 0 END), 0) AS debitos, "
            + "COALESCE(SUM(CASE WHEN lp.tipo = 'C' THEN lp.valor ELSE 0 END), 0) AS creditos, "
            + "COALESCE(SUM(CASE WHEN lp.tipo = 'C' THEN lp.valor ELSE -lp.valor END), 0) AS resultado "
            + "FROM LancamentoContabil lc JOIN LancamentoPartida lp ON lp.lancamentoId = lc.id "
            + "JOIN PlanoContas pc ON pc.id = lp.planoContasId "
            + "WHERE lc.empresaId = :empresaId AND lc.deletedAt IS NULL "
            + "AND lp.deletedAt IS NULL AND pc.deletedAt IS NULL "
            + "AND (:inicio IS NULL OR lc.dataLancamento >= :inicio) "
            + "AND (:fim IS NULL OR lc.dataLancamento <= :fim) "
            + "GROUP BY pc.codigo, pc.descricao, pc.natureza "
            + "ORDER BY pc.codigo")
    java.util.List<DreRow> relatorioDre(@Param("empresaId") Long empresaId,
            @Param("inicio") java.time.LocalDate inicio, @Param("fim") java.time.LocalDate fim,
            Pageable pageable);

    interface DreRow {
        String getCodigo();
        String getDescricao();
        String getNatureza();
        java.math.BigDecimal getDebitos();
        java.math.BigDecimal getCreditos();
        java.math.BigDecimal getResultado();
    }
}
