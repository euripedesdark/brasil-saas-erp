package br.com.brasil_saas.ehs.repository;

import br.com.brasil_saas.ehs.model.EhsAcao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EhsAcaoRepository extends JpaRepository<EhsAcao, Long> {
    List<EhsAcao> findByEmpresaIdOrderByPrazoAscIdDesc(Long empresaId);
    List<EhsAcao> findByEmpresaIdOrderByIdDesc(Long empresaId);
    Optional<EhsAcao> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<EhsAcao> findTopByEmpresaIdOrderByIdDesc(Long empresaId);
}
