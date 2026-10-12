package br.com.brasil_saas.projetos.repository;
import br.com.brasil_saas.projetos.model.PrjProjeto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PrjProjetoRepository extends JpaRepository<PrjProjeto, Long> {
    Optional<PrjProjeto> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    List<PrjProjeto> findByEmpresaIdAndDeletedAtIsNull(Long empresaId);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
