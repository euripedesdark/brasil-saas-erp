package br.com.brasil_saas.core.repository;

import br.com.brasil_saas.core.model.Auditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findByEmpresaIdOrderByCreatedAtDesc(Long empresaId, Pageable pageable);
}
