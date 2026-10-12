package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GovControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GovControleRepository extends JpaRepository<GovControle, Long> {
    List<GovControle> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<GovControle> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
