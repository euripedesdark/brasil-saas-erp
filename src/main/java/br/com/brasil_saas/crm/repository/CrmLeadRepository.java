package br.com.brasil_saas.crm.repository;
import br.com.brasil_saas.crm.model.CrmLead;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface CrmLeadRepository extends JpaRepository<CrmLead, Long> {
    Optional<CrmLead> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<CrmLead> findByEmpresaIdAndDeletedAtIsNull(Long empresaId);
    List<CrmLead> findByEmpresaIdAndEtapaAndDeletedAtIsNull(Long empresaId, String etapa);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
