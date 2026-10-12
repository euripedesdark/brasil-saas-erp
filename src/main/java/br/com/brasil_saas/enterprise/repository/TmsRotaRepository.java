package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsRota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TmsRotaRepository extends JpaRepository<TmsRota, Long> {
    List<TmsRota> findByEmpresaIdOrderByCodigoAsc(Long empresaId);
}
