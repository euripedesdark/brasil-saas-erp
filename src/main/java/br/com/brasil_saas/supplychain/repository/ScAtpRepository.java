package br.com.brasil_saas.supplychain.repository;

import br.com.brasil_saas.supplychain.model.ScAtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScAtpRepository extends JpaRepository<ScAtp, Long> {
    List<ScAtp> findByEmpresaIdOrderByDataDesc(Long empresaId);
    List<ScAtp> findByEmpresaIdAndProdutoIdOrderByDataDesc(Long empresaId, Long produtoId);
    Optional<ScAtp> findByEmpresaIdAndProdutoIdAndLocalIdAndData(
            Long empresaId, Long produtoId, Long localId, java.time.LocalDate data);
}
