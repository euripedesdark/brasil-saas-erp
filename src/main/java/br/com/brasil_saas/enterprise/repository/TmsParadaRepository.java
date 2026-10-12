package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsParada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TmsParadaRepository extends JpaRepository<TmsParada, Long> {
    boolean existsByEmpresaIdAndReferenciaTipoAndReferenciaIdAndStatusNot(
            Long empresaId, String referenciaTipo, Long referenciaId, String status);
}
