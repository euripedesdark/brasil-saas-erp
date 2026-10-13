package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TmsEventoRepository extends JpaRepository<TmsEvento, Long> {
    List<TmsEvento> findByEmpresaIdOrderByDataEventoDesc(Long empresaId);
}
