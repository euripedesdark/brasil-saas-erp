package br.com.brasil_saas.contratosvenda.repository;

import br.com.brasil_saas.contratosvenda.model.ContratoVenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContratoVendaRepository extends JpaRepository<ContratoVenda, Long> {
    List<ContratoVenda> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);
    List<ContratoVenda> findByEmpresaIdAndStatusAndDeletedAtIsNullOrderByIdDesc(Long empresaId, String status);
    Optional<ContratoVenda> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    long countByEmpresaIdAndStatusAndDeletedAtIsNull(Long empresaId, String status);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
