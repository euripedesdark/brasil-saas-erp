package br.com.brasil_saas.cadastro.repository;

import br.com.brasil_saas.cadastro.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    Page<Produto> findByEmpresaIdAndDeletedAtIsNull(Long empresaId, Pageable pageable);

    @Query("SELECT p FROM Produto p WHERE p.empresaId = :empresaId AND p.deletedAt IS NULL AND " +
           "(:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:nome AS String), '%'))) AND " +
           "(:codigo IS NULL OR p.codigo = :codigo) AND " +
           "(:categoriaId IS NULL OR p.categoria.id = :categoriaId) AND " +
           "(:marcaId IS NULL OR p.marca.id = :marcaId) AND " +
           "(:ativo IS NULL OR p.ativo = :ativo)")
    Page<Produto> buscar(@Param("empresaId") Long empresaId, @Param("nome") String nome, @Param("codigo") String codigo,
                         @Param("categoriaId") Long categoriaId, @Param("marcaId") Long marcaId,
                         @Param("ativo") Boolean ativo, Pageable pageable);

    boolean existsByEmpresaIdAndCodigoAndDeletedAtIsNull(Long empresaId, String codigo);

    /**
     * Procura pelo codigo de barras, que e o que casa o item de uma nota com
     * um produto ja cadastrado.
     *
     * <p>Existe porque o cEAN e o mesmo fornecedor para fornecedor, ao contrario
     * do cProd, e o ERP descartava o cEAN da nota. Preferivel ao nome: dois
     * produtos podem ter o mesmo nome e o cEAN nunca casa com o errado.
     */
    Optional<Produto> findByEmpresaIdAndCodigoBarrasAndDeletedAtIsNull(Long empresaId, String codigoBarras);
    Optional<Produto> findByEmpresaIdAndCodigoIgnoreCaseAndDeletedAtIsNull(Long empresaId, String codigo);
    boolean existsByEmpresaIdAndCodigoAndIdNotAndDeletedAtIsNull(Long empresaId, String codigo, Long id);
    Optional<Produto> findFirstByCodigoIgnoreCaseAndDeletedAtIsNull(String codigo);
    java.util.Optional<br.com.brasil_saas.cadastro.model.Produto> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
    @Query("SELECT p FROM Produto p WHERE p.empresaId = :empresaId AND p.deletedAt IS NULL AND ("
            + "LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS String), '%')) OR "
            + "LOWER(p.codigo) LIKE LOWER(CONCAT('%', CAST(:termo AS String), '%'))) AND "
            + "(:categoriaId IS NULL OR p.categoria.id = :categoriaId) AND "
            + "(:marcaId IS NULL OR p.marca.id = :marcaId) AND "
            + "(:ativo IS NULL OR p.ativo = :ativo)")
    Page<Produto> buscaGeral(@Param("empresaId") Long empresaId, @Param("termo") String termo,
            @Param("categoriaId") Long categoriaId, @Param("marcaId") Long marcaId,
            @Param("ativo") Boolean ativo, Pageable pageable);
}
