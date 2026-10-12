package br.com.brasil_saas.dms.repository;
import br.com.brasil_saas.dms.model.DmsDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface DmsDocumentoRepository extends JpaRepository<DmsDocumento, Long> {
    Optional<DmsDocumento> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<DmsDocumento> findByEmpresaIdAndDeletedAtIsNull(Long empresaId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
