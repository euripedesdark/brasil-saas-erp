package br.com.brasil_saas.agenda.repository;

import br.com.brasil_saas.agenda.model.EventoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventoAgendaRepository extends JpaRepository<EventoAgenda, Long> {
    List<EventoAgenda> findByEmpresaIdAndDeletedAtIsNullOrderByInicioAsc(Long empresaId);
    Optional<EventoAgenda> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);

    @Query("""
        SELECT e FROM EventoAgenda e
        WHERE e.empresaId = :empresaId AND e.deletedAt IS NULL
          AND e.inicio >= :de AND e.inicio < :ate
        ORDER BY e.inicio
        """)
    List<EventoAgenda> noPeriodo(@Param("empresaId") Long empresaId,
                                 @Param("de") LocalDateTime de,
                                 @Param("ate") LocalDateTime ate);

    long countByEmpresaIdAndStatusAndDeletedAtIsNull(Long empresaId, String status);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
