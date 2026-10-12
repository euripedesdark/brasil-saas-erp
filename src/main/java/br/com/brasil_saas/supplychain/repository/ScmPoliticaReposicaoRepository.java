package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScmPoliticaReposicao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScmPoliticaReposicaoRepository extends JpaRepository<ScmPoliticaReposicao, Long> {
    List<ScmPoliticaReposicao> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    Optional<ScmPoliticaReposicao> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
