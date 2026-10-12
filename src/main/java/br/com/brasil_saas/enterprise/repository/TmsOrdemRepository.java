package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TmsOrdem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TmsOrdemRepository extends JpaRepository<TmsOrdem, Long> {
}
