package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsFrete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TmsFreteRepository extends JpaRepository<TmsFrete, Long> {
    List<TmsFrete> findByEmpresaIdOrderByIdDesc(Long empresaId);
}
