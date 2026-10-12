package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.FinConsolidacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinConsolidacaoRepository extends JpaRepository<FinConsolidacao, Long> {
    List<FinConsolidacao> findByEmpresaIdOrderByIdDesc(Long empresaId);

    Optional<FinConsolidacao> findByIdAndEmpresaId(Long id, Long empresaId);
}
