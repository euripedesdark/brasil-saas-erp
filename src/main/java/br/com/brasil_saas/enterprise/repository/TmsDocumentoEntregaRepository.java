package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsDocumentoEntrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TmsDocumentoEntregaRepository extends JpaRepository<TmsDocumentoEntrega, Long> {
    List<TmsDocumentoEntrega> findByEmpresaIdOrderByIdDesc(Long empresaId);
}
