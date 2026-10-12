package br.com.brasil_saas.plm.repository;

import br.com.brasil_saas.plm.model.PlmMudanca;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlmMudancaRepository extends JpaRepository<PlmMudanca, Long> {
    List<PlmMudanca> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<PlmMudanca> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PlmMudanca m where m.id = :id and m.empresaId = :empresaId and m.deletedAt is null")
    Optional<PlmMudanca> findByIdForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
    List<PlmMudanca> findByEmpresaIdOrderByIdDesc(Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByIdAndEmpresaIdAndStatusIn(Long id, Long empresaId, java.util.Collection<String> status);
    Optional<PlmMudanca> findByIdAndEmpresaId(Long id, Long empresaId);
}
