package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScRota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScRotaRepository extends JpaRepository<ScRota, Long> {
    List<ScRota> findByEmpresaIdOrderByOrigemAscDestinoAsc(Long empresaId);
}
