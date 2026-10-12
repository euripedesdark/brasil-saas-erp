package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TmsTrackingRepository extends JpaRepository<TmsTracking, Long> {
    List<TmsTracking> findByEmpresaIdOrderByUltimaAtualizacaoDesc(Long empresaId);
    Optional<TmsTracking> findByEmpresaIdAndOrdemId(Long empresaId, Long ordemId);
}
