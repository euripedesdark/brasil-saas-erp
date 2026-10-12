package br.com.brasil_saas.core.repository;

import br.com.brasil_saas.core.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findByCnpj(String cnpj);
    boolean existsByIdAndDeletedAtIsNull(Long id);
}
