package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.GrcPlanoAcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrcPlanoAcaoRepository extends JpaRepository<GrcPlanoAcao, Long> {
    List<GrcPlanoAcao> findByEmpresaIdOrderByPrazoAscIdDesc(Long empresaId);
    Optional<GrcPlanoAcao> findByIdAndEmpresaId(Long id, Long empresaId);
}
