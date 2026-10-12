package br.com.brasil_saas.contabilidade.repository;

import br.com.brasil_saas.contabilidade.model.FechamentoCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FechamentoCheckRepository extends JpaRepository<FechamentoCheck, Long> {
    List<FechamentoCheck> findByEmpresaIdAndPeriodoIdOrderById(Long empresaId, Long periodoId);
}
