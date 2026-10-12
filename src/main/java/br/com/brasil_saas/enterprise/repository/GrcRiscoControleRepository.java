package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcRiscoControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrcRiscoControleRepository extends JpaRepository<GrcRiscoControle, Long> {
    List<GrcRiscoControle> findByEmpresaIdOrderByIdAsc(Long empresaId);
    boolean existsByEmpresaIdAndRiscoIdAndControleId(Long empresaId, Long riscoId, Long controleId);
}
