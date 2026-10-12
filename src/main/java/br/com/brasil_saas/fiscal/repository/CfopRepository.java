package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Cfop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CfopRepository extends JpaRepository<Cfop, Long> {
    List<Cfop> findByTipoOperacaoOrderByCodigo(String tipoOperacao);
    List<Cfop> findAllByOrderByCodigo();
    Optional<Cfop> findByCodigo(String codigo);
    List<Cfop> findByTipoOperacaoAndCodigoInOrderByCodigo(String tipoOperacao, java.util.Collection<String> codigos);
}
