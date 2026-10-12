package br.com.brasil_saas.plm.repository;

import br.com.brasil_saas.plm.model.PlmAprovacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlmAprovacaoRepository extends JpaRepository<PlmAprovacao, Long> {
    List<PlmAprovacao> findByEmpresaIdAndMudancaIdOrderByEtapaAsc(Long empresaId, Long mudancaId);
    Optional<PlmAprovacao> findByEmpresaIdAndMudancaIdAndEtapa(Long empresaId, Long mudancaId, Integer etapa);
    long countByEmpresaIdAndMudancaIdAndObrigatoriaTrueAndDecisao(Long empresaId, Long mudancaId, String decisao);
    List<PlmAprovacao> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<PlmAprovacao> findByIdAndEmpresaId(Long id, Long empresaId);
}
