package br.com.brasil_saas.ehs.repository;

import br.com.brasil_saas.ehs.model.EhsPermissaoTrabalho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EhsPermissaoTrabalhoRepository extends JpaRepository<EhsPermissaoTrabalho, Long> {
    List<EhsPermissaoTrabalho> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<EhsPermissaoTrabalho> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
