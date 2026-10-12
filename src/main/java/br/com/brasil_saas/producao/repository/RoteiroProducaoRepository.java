package br.com.brasil_saas.producao.repository;
import br.com.brasil_saas.producao.model.RoteiroProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RoteiroProducaoRepository extends JpaRepository<RoteiroProducao,Long>{
 List<RoteiroProducao> findByEmpresaIdAndDeletedAtIsNullOrderByProdutoIdAscCodigoAscVersaoDesc(Long empresaId);
 List<RoteiroProducao> findByEmpresaIdAndProdutoIdAndDeletedAtIsNullOrderByVersaoDesc(Long empresaId,Long produtoId);
 Optional<RoteiroProducao> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id,Long empresaId);
    boolean existsByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<RoteiroProducao> findByEmpresaIdAndProdutoIdAndIdNot(Long empresaId, Long produtoId, Long id);
    Optional<RoteiroProducao> findByIdAndEmpresaId(Long id, Long empresaId);
}
