package br.com.brasil_saas.ativos.repository;
import br.com.brasil_saas.ativos.model.AtivoImobilizado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AtivoImobilizadoRepository extends JpaRepository<AtivoImobilizado,Long>{
 List<AtivoImobilizado> findAllByEmpresaIdAndDeletedAtIsNullOrderByCodigo(Long empresaId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}