package br.com.brasil_saas.shared.repository;

import br.com.brasil_saas.shared.model.IntegrationEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Outbox: bloqueio pessimista com SKIP LOCKED via Hibernate.
 * O caller deve executar o claim dentro de uma transacao.
 */
@Repository
public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM IntegrationEvent e WHERE e.status = :status ORDER BY e.createdAt, e.id")
    List<IntegrationEvent> findPendingForClaim(@Param("status") String status, Pageable pageable);

    default List<IntegrationEvent> claimPending(int n) {
        if (n <= 0) return List.of();
        return findPendingForClaim(IntegrationEvent.PENDING,
                org.springframework.data.domain.PageRequest.of(0, n));
    }
}
