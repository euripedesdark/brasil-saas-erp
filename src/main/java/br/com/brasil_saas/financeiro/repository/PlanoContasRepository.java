package br.com.brasil_saas.financeiro.repository;

import br.com.brasil_saas.financeiro.model.PlanoContas;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanoContasRepository extends JpaRepository<PlanoContas, Long> {
    List<PlanoContas> findByEmpresaIdAndAtivaTrueAndDeletedAtIsNullOrderByCodigo(Long empresaId);
    List<PlanoContas> findByEmpresaIdAndDeletedAtIsNullOrderByCodigo(Long empresaId);
    Optional<PlanoContas> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
