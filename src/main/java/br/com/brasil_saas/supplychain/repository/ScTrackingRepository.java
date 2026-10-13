package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScTrackingRepository extends JpaRepository<ScTracking, Long> {
    List<ScTracking> findByEmpresaIdAndCargaIdOrderByDataEventoAsc(Long empresaId, Long cargaId);
}
