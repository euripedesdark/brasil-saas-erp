package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GovRisco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GovRiscoRepository extends JpaRepository<GovRisco, Long> {
    List<GovRisco> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<GovRisco> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
