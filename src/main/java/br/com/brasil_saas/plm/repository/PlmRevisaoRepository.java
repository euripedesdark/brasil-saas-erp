package br.com.brasil_saas.plm.repository;

import br.com.brasil_saas.plm.model.PlmRevisao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlmRevisaoRepository extends JpaRepository<PlmRevisao, Long> {
    List<PlmRevisao> findByEmpresaIdAndProdutoIdOrderByIdDesc(Long empresaId, Long produtoId);
    Optional<PlmRevisao> findByIdAndEmpresaId(Long id, Long empresaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PlmRevisao r where r.id = :id and r.empresaId = :empresaId")
    Optional<PlmRevisao> findByIdForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
    List<PlmRevisao> findByEmpresaIdAndProdutoIdAndIdNotAndStatus(Long empresaId, Long produtoId, Long id, String status);
    Optional<PlmRevisao> findTopByEmpresaIdOrderByIdDesc(Long empresaId);
    List<PlmRevisao> findByEmpresaIdOrderByIdDesc(Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
