package br.com.brasil_saas.contabilidade.repository;

import br.com.brasil_saas.contabilidade.model.RegraLancamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegraLancamentoRepository extends JpaRepository<RegraLancamento, Long> {
    Optional<RegraLancamento> findFirstByEmpresaIdAndCodigoAndAtivoTrue(Long empresaId, String codigo);
    Optional<RegraLancamento> findByEmpresaIdAndCodigo(Long empresaId, String codigo);
    java.util.List<RegraLancamento> findByEmpresaIdOrderByCodigo(Long empresaId);
}
