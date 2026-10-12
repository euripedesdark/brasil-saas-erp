package br.com.brasil_saas.contabilidade.repository;

import br.com.brasil_saas.contabilidade.model.CtbPendencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CtbPendenciaRepository extends JpaRepository<CtbPendencia, Long> {
    Optional<CtbPendencia> findByEmpresaIdAndOrigemTipoAndOrigemIdAndEvento(
            Long empresaId, String origemTipo, Long origemId, String evento);

    Optional<CtbPendencia> findByEmpresaIdAndOrigemTipoAndOrigemIdAndEventoAndStatus(
            Long empresaId, String origemTipo, Long origemId, String evento, String status);
}
