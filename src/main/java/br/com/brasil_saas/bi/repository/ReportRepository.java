package br.com.brasil_saas.bi.repository;

import br.com.brasil_saas.bi.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    
    List<Report> findByEmpresaIdOrderByNameAsc(Long empresaId);
    
    @EntityGraph(attributePaths = "parameters")
    Optional<Report> findByIdAndEmpresaId(Long id, Long empresaId);
    
    @EntityGraph(attributePaths = "parameters")
    Page<Report> findByEmpresaId(Long empresaId, Pageable pageable);
    
    List<Report> findByEmpresaIdAndCategoryOrderByNameAsc(Long empresaId, String category);
    
    List<Report> findByEmpresaIdAndIsScheduledTrue(Long empresaId);
}
