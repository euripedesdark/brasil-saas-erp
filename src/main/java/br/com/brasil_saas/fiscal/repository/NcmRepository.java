package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Ncm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NcmRepository extends JpaRepository<Ncm, Long> {
    Page<Ncm> findAllByOrderByCodigo(Pageable pageable);

    Page<Ncm> findByDescricaoContainingIgnoreCaseOrderByCodigo(String descricao, Pageable pageable);
    Optional<Ncm> findByCodigo(String codigo);

    @Query("SELECT c FROM Ncm c WHERE c.codigo = :codigo ORDER BY c.codigo")
    java.util.List<Ncm> buscarPorCodigoExato(@Param("codigo") String codigo, Pageable pageable);
}
