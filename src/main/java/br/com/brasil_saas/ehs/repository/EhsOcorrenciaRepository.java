package br.com.brasil_saas.ehs.repository;

import br.com.brasil_saas.ehs.model.EhsOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EhsOcorrenciaRepository extends JpaRepository<EhsOcorrencia, Long> {
    List<EhsOcorrencia> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<EhsOcorrencia> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
