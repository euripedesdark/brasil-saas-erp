package br.com.brasil_saas.plm.repository;

import br.com.brasil_saas.plm.model.PlmDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlmDocumentoRepository extends JpaRepository<PlmDocumento, Long> {
    List<PlmDocumento> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<PlmDocumento> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<PlmDocumento> findByEmpresaIdAndMudancaIdAndDeletedAtIsNullOrderByIdDesc(
            Long empresaId, Long mudancaId);
    Optional<PlmDocumento> findTopByEmpresaIdOrderByIdDesc(Long empresaId);
    boolean existsByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
