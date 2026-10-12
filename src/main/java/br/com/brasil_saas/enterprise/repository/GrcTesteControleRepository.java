package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcTesteControle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrcTesteControleRepository extends JpaRepository<GrcTesteControle, Long> {
    List<GrcTesteControle> findByEmpresaIdOrderByPeriodoDescIdDesc(Long empresaId);
    Optional<GrcTesteControle> findByEmpresaIdAndControleIdAndPeriodo(
            Long empresaId, Long controleId, String periodo);
}
