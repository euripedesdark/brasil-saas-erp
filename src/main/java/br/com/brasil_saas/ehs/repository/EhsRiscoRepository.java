package br.com.brasil_saas.ehs.repository;

import br.com.brasil_saas.ehs.model.EhsRisco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EhsRiscoRepository extends JpaRepository<EhsRisco, Long> {
    List<EhsRisco> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<EhsRisco> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
