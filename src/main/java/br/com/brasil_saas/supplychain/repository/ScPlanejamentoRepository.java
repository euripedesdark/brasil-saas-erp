package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScPlanejamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScPlanejamentoRepository extends JpaRepository<ScPlanejamento, Long> {
    Optional<ScPlanejamento> findByEmpresaIdAndCodigo(Long empresaId, String codigo);
}
