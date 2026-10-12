package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.IntEndpoint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntEndpointRepository extends JpaRepository<IntEndpoint, Long> {
    List<IntEndpoint> findByEmpresaIdOrderByIdDesc(Long empresaId, Pageable pageable);

    default List<IntEndpoint> listarRecentes(Long empresaId, int limite) {
        return findByEmpresaIdOrderByIdDesc(empresaId,
                org.springframework.data.domain.PageRequest.of(0, limite));
    }

    Optional<IntEndpoint> findByEmpresaIdAndCodigoAndAtivoTrue(Long empresaId, String codigo);

    List<IntEndpoint> findByEmpresaIdAndAtivoTrue(Long empresaId);
}
