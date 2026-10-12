package br.com.brasil_saas.contabilidade.repository;

import br.com.brasil_saas.contabilidade.model.PeriodoContabilEnt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PeriodoContabilEntRepository extends JpaRepository<PeriodoContabilEnt, Long> {
    Optional<PeriodoContabilEnt> findByIdAndEmpresaId(Long id, Long empresaId);
}
