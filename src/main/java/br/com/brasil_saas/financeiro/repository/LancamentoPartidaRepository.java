package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.LancamentoPartida;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
