package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsFechamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TmsFechamentoRepository extends JpaRepository<TmsFechamento, Long> {
    List<TmsFechamento> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<TmsFechamento> findByEmpresaIdAndOrdemId(Long empresaId, Long ordemId);
}
