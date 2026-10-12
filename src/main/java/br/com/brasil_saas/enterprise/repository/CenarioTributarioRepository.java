package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.CenarioTributario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CenarioTributarioRepository extends JpaRepository<CenarioTributario, Long> {
    List<CenarioTributario> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<CenarioTributario> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
