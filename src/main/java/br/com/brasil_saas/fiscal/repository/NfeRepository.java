package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Nfe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NfeRepository extends JpaRepository<Nfe, Long> {
    @Query("select n from Nfe n where n.empresaId = :empresaId and n.tipoOperacao = :tipo and n.dataEmissao >= :de and n.dataEmissao < :ate and n.deletedAt is null")
    java.util.List<Nfe> findNoPeriodoPorTipo(@Param("empresaId") Long empresaId, @Param("tipo") String tipo, @Param("de") java.time.LocalDateTime de, @Param("ate") java.time.LocalDateTime ate);
    Optional<Nfe> findByEmpresaIdAndChaveAcesso(Long empresaId, String chaveAcesso);
    Page<Nfe> findByEmpresaIdAndStatusOrderByDataEmissaoDesc(Long empresaId, String status, Pageable pageable);
    Page<Nfe> findByEmpresaIdOrderByDataEmissaoDesc(Long empresaId, Pageable pageable);
    java.util.Optional<br.com.brasil_saas.fiscal.model.Nfe> findByEmpresaIdAndChaveAcessoAndDeletedAtIsNull(Long empresaId, String chaveAcesso);
    org.springframework.data.domain.Page<br.com.brasil_saas.fiscal.model.Nfe> findByEmpresaIdAndTipoOperacaoAndDeletedAtIsNull(Long empresaId, String tipoOperacao, org.springframework.data.domain.Pageable pageable);
    java.util.Optional<br.com.brasil_saas.fiscal.model.Nfe> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    java.util.List<Nfe> findByEmpresaIdAndPedidoCompraIdAndDeletedAtIsNull(Long empresaId, Long pedidoCompraId);
    java.util.Optional<Nfe> findTopByEmpresaIdAndSerieAndDeletedAtIsNullOrderByNumeroDesc(Long empresaId, String serie);

    @Query(value = "select pg_advisory_xact_lock(hashtext(concat('brasil_saas:nfe:', :empresaId, ':', :serie)))", nativeQuery = true)
    void lockSequence(@Param("empresaId") Long empresaId, @Param("serie") String serie);
    java.util.List<Nfe> findByEmpresaIdAndTipoOperacaoAndDataEmissaoBetweenAndDeletedAtIsNull(Long empresaId, String tipoOperacao, java.time.LocalDateTime de, java.time.LocalDateTime ate);

    @Query("select count(n) from Nfe n where n.empresaId = :empresaId and n.dataEmissao >= :de and n.dataEmissao < :ate and n.deletedAt is null")
    long countNoPeriodo(@Param("empresaId") Long empresaId, @Param("de") java.time.LocalDateTime de, @Param("ate") java.time.LocalDateTime ate);

    @Query("SELECT n.numero AS numero, n.serie AS serie, n.naturezaOperacao AS naturezaOperacao, "
            + "n.dataEmissao AS dataEmissao, n.status AS status, n.valorTotal AS valorTotal, "
            + "COUNT(n.id) AS quantidadeNotas "
            + "FROM Nfe n WHERE n.empresaId = :empresaId AND n.deletedAt IS NULL "
            + "AND (:inicio IS NULL OR n.dataEmissao >= :inicio) "
            + "AND (:fim IS NULL OR n.dataEmissao <= :fim) "
            + "GROUP BY n.id, n.numero, n.serie, n.naturezaOperacao, n.dataEmissao, n.status, n.valorTotal "
            + "ORDER BY n.dataEmissao DESC")
    java.util.List<FiscalRow> relatorioFiscal(@Param("empresaId") Long empresaId,
            @Param("inicio") java.time.LocalDateTime inicio, @Param("fim") java.time.LocalDateTime fim,
            Pageable pageable);

    interface FiscalRow {
        Long getNumero();
        String getSerie();
        String getNaturezaOperacao();
        java.time.LocalDateTime getDataEmissao();
        String getStatus();
        java.math.BigDecimal getValorTotal();
        long getQuantidadeNotas();
    }
}
