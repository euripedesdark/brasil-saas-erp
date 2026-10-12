package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScmOrdemTransporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScmOrdemTransporteRepository extends JpaRepository<ScmOrdemTransporte, Long> {
    List<ScmOrdemTransporte> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<ScmOrdemTransporte> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
