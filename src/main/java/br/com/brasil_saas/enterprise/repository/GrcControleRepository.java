package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrcControleRepository extends JpaRepository<GrcControle, Long> {
    List<GrcControle> findByEmpresaIdOrderByCodigoAsc(Long empresaId);
    Optional<GrcControle> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
