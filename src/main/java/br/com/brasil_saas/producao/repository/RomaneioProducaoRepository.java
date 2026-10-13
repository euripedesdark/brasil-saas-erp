package br.com.brasil_saas.producao.repository;

import br.com.brasil_saas.producao.model.RomaneioProducao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RomaneioProducaoRepository extends JpaRepository<RomaneioProducao, Long> {
    @EntityGraph(attributePaths = {"itens", "producao", "producao.itens"})
    List<RomaneioProducao> findByEmpresaIdAndDeletedAtIsNullOrderByDataRomaneioDesc(Long empresaId);
}
