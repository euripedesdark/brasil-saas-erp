package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScCarga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ScCargaRepository extends JpaRepository<ScCarga, Long> {}
