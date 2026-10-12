package br.com.brasil_saas.workflow.repository;
import br.com.brasil_saas.workflow.model.WkfInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface WkfInstanceRepository extends JpaRepository<WkfInstance, Long> {
    Optional<WkfInstance> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<WkfInstance> findByEmpresaIdAndDeletedAtIsNull(Long empresaId);
    List<WkfInstance> findByEmpresaIdAndStatusAndDeletedAtIsNull(Long empresaId, String status);
    List<WkfInstance> findByEmpresaIdAndEntidadeTipoAndEntidadeIdAndDeletedAtIsNull(Long empresaId, String tipo, Long entidadeId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
