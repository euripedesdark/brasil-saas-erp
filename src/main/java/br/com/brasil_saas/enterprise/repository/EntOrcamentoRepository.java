package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.EntOrcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntOrcamentoRepository extends JpaRepository<EntOrcamento, Long> {
    List<EntOrcamento> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<EntOrcamento> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
