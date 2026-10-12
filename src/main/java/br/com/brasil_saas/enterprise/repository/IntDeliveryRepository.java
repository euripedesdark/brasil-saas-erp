package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.IntDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntDeliveryRepository extends JpaRepository<IntDelivery, Long> {
    List<IntDelivery> findByEmpresaIdOrderByIdDesc(Long empresaId, Pageable pageable);

    Optional<IntDelivery> findByIdAndEmpresaId(Long id, Long empresaId);
}
