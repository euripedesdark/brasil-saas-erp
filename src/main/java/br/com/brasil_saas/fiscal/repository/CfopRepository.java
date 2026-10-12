package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Cfop;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CfopRepository extends JpaRepository<Cfop, Long> {
    List<Cfop> findByTipoOperacaoOrderByCodigo(String tipoOperacao);
    List<Cfop> findAllByOrderByCodigo();
    Optional<Cfop> findByCodigo(String codigo);
    List<Cfop> findByTipoOperacaoAndCodigoInOrderByCodigo(String tipoOperacao, java.util.Collection<String> codigos);

    @Query("SELECT c FROM Cfop c WHERE FUNCTION('fiscal_normaliza_codigo', c.codigo) = :cod OR FUNCTION('fiscal_normaliza_codigo', c.codigo) LIKE :prefixo ORDER BY c.codigo")
    java.util.List<Cfop> buscarPorCodigoNormalizado(@Param("cod") String cod, @Param("prefixo") String prefixo, Pageable pageable);

    @Query("SELECT c FROM Cfop c WHERE c.codigo = :codigo ORDER BY c.codigo")
    java.util.List<Cfop> buscarPorCodigoExato(@Param("codigo") String codigo, Pageable pageable);
}
