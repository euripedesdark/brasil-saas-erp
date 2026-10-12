package br.com.brasil_saas.plm.repository;

import br.com.brasil_saas.plm.model.PlmEfeito;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlmEfeitoRepository extends JpaRepository<PlmEfeito, Long> {
    List<PlmEfeito> findByEmpresaIdAndMudancaIdOrderByOrdemExecucaoAscIdAsc(Long empresaId, Long mudancaId);
    long countByEmpresaIdAndMudancaId(Long empresaId, Long mudancaId);
    Optional<PlmEfeito> findTopByEmpresaIdAndMudancaIdOrderByOrdemExecucaoDesc(Long empresaId, Long mudancaId);
    List<PlmEfeito> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<PlmEfeito> findByIdAndEmpresaId(Long id, Long empresaId);
}
