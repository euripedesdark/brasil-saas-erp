package br.com.brasil_saas.contabilidade.repository;

import br.com.brasil_saas.contabilidade.model.ContRateio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContRateioRepository extends JpaRepository<ContRateio, Long> {
}
