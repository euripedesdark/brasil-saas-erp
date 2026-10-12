package br.com.brasil_saas.servicos.repository;

import br.com.brasil_saas.servicos.model.SrvContrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SrvContratoRepository extends JpaRepository<SrvContrato, Long> {
    List<SrvContrato> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<SrvContrato> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
