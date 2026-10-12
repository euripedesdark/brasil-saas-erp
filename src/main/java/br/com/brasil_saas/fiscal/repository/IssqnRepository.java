package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Issqn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssqnRepository extends JpaRepository<Issqn, Long> {
    Page<Issqn> findAllByOrderByCodigo(Pageable pageable);

    Optional<Issqn> findByCodIbge(String codIbge);
    List<Issqn> findByUfOrderByMunicipio(String uf);

    @Query("SELECT c FROM Issqn c WHERE c.codigo = :codigo ORDER BY c.codigo")
    java.util.List<Issqn> buscarPorCodigoExato(@Param("codigo") String codigo, Pageable pageable);
}
