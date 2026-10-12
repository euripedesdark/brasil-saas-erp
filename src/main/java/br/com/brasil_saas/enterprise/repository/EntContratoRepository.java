package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.EntContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntContratoRepository extends JpaRepository<EntContrato, Long> {
    List<EntContrato> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<EntContrato> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
