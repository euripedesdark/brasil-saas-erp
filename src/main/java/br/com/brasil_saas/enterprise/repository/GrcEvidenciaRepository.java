package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcEvidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrcEvidenciaRepository extends JpaRepository<GrcEvidencia, Long> {
    List<GrcEvidencia> findByEmpresaIdOrderByIdDesc(Long empresaId);
}
