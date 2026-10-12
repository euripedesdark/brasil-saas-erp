package br.com.brasil_saas.producao.repository;

import br.com.brasil_saas.producao.model.EstruturaProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstruturaProdutoRepository extends JpaRepository<EstruturaProduto, Long> {
    List<EstruturaProduto> findByEmpresaIdAndProdutoPaiIdAndDeletedAtIsNullOrderByNivelAscIdAsc(Long empresaId, Long produtoPaiId);
    Optional<EstruturaProduto> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    Optional<EstruturaProduto> findByEmpresaIdAndProdutoPaiIdAndProdutoFilhoIdAndDeletedAtIsNull(
            Long empresaId, Long produtoPaiId, Long produtoFilhoId);
    boolean existsByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
