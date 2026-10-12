package br.com.brasil_saas.ehs.repository;

import br.com.brasil_saas.ehs.model.EhsInspecao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EhsInspecaoRepository extends JpaRepository<EhsInspecao, Long> {
    List<EhsInspecao> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<EhsInspecao> findByIdAndEmpresaId(Long id, Long empresaId);
    boolean existsByIdAndEmpresaId(Long id, Long empresaId);
}
