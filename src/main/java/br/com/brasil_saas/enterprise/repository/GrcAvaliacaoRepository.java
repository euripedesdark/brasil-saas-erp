package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcAvaliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrcAvaliacaoRepository extends JpaRepository<GrcAvaliacao, Long> {
    List<GrcAvaliacao> findByEmpresaIdOrderByPeriodoDescIdDesc(Long empresaId);
    Optional<GrcAvaliacao> findByEmpresaIdAndRiscoIdAndPeriodo(
            Long empresaId, Long riscoId, String periodo);
}
