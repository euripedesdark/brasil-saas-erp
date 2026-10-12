package br.com.brasil_saas.enterprise.repository;

import br.com.brasil_saas.enterprise.model.MetaComercial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("enterpriseMetaComercialRepository")
public interface MetaComercialRepository extends JpaRepository<MetaComercial, Long> {
    List<MetaComercial> findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(Long empresaId);

    Optional<MetaComercial> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
}
