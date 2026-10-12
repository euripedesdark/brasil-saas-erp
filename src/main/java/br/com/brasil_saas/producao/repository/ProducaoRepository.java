package br.com.brasil_saas.producao.repository;

import br.com.brasil_saas.producao.model.Producao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProducaoRepository extends JpaRepository<Producao, Long> {
    Optional<Producao> findByNumeroAndEmpresaId(String numero, Long empresaId);

    /**
     * Ordens de uma empresa, com os itens na mesma consulta.
     *
     * O service antes fazia findAll() e filtrava em Java: trazia as ordens de
     * TODOS os tenants para a memoria do processo e devolvia a lista filtrada.
     * Alem do custo, os itens sao lazy e a sessao ja tinha fechado quando o
     * Jackson ia serializar — com uma ordem cadastrada, a listagem devolvia 500
     * (LazyInitializationException). O EntityGraph traz os itens na consulta.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "itens")
    java.util.List<Producao> findByEmpresaIdOrderByDataInicioDesc(Long empresaId);

    @Query("SELECT o.numero AS numero, o.dataInicio AS dataInicio, o.dataFim AS dataFim, "
            + "COALESCE(pr.nome, CONCAT('Produto #', o.produtoFinalId)) AS produtoFinal, "
            + "o.quantidadePlanejada AS quantidadePlanejada, o.custoTotal AS custoTotal, "
            + "o.status AS status, o.tipoProducao AS tipoProducao "
            + "FROM Producao o LEFT JOIN Produto pr ON pr.id = o.produtoFinalId "
            + "WHERE o.empresaId = :empresaId AND o.deletedAt IS NULL "
            + "AND (:inicio IS NULL OR o.dataInicio >= :inicio) "
            + "AND (:fim IS NULL OR o.dataInicio <= :fim) "
            + "ORDER BY o.dataInicio DESC")
    java.util.List<ProducaoRow> relatorioProducao(@Param("empresaId") Long empresaId,
            @Param("inicio") java.time.LocalDateTime inicio, @Param("fim") java.time.LocalDateTime fim,
            Pageable pageable);

    interface ProducaoRow {
        String getNumero();
        java.time.LocalDateTime getDataInicio();
        java.time.LocalDateTime getDataFim();
        String getProdutoFinal();
        java.math.BigDecimal getQuantidadePlanejada();
        java.math.BigDecimal getCustoTotal();
        String getStatus();
        String getTipoProducao();
    }
}
