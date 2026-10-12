package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScFrete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScFreteRepository extends JpaRepository<ScFrete, Long> {
    Optional<ScFrete> findByEmpresaIdAndCargaId(Long empresaId, Long cargaId);
}
