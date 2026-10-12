package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.TesourariaPrevisao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TesourariaPrevisaoRepository extends JpaRepository<TesourariaPrevisao, Long> {
    List<TesourariaPrevisao> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<TesourariaPrevisao> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
