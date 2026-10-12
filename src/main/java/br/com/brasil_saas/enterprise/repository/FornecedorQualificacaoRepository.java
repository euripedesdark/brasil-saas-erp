package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.FornecedorQualificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FornecedorQualificacaoRepository extends JpaRepository<FornecedorQualificacao, Long> {
    List<FornecedorQualificacao> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<FornecedorQualificacao> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
