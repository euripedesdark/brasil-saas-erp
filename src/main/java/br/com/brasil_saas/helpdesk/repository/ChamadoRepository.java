package br.com.brasil_saas.helpdesk.repository;

import br.com.brasil_saas.helpdesk.model.Chamado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChamadoRepository extends JpaRepository<Chamado, Long> {
    List<Chamado> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    List<Chamado> findByEmpresaIdAndStatusAndDeletedAtIsNullOrderByIdDesc(Long empresaId, String status);
    Optional<Chamado> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    long countByEmpresaIdAndStatusAndDeletedAtIsNull(Long empresaId, String status);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
