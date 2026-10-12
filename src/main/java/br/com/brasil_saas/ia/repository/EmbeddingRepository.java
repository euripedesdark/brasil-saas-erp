package br.com.brasil_saas.ia.repository;

import br.com.brasil_saas.ia.model.Embedding;
import br.com.brasil_saas.shared.repository.TenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbeddingRepository extends TenantRepository<Embedding, Long> {

    List<Embedding> findByEmpresaIdAndEntidadeTipo(Long empresaId, String entidadeTipo);

    List<Embedding> findByEmpresaIdAndEntidadeTipoAndEntidadeId(Long empresaId, String entidadeTipo, Long entidadeId);

    List<Embedding> findByEmpresaIdAndModelo(Long empresaId, String modelo);

    List<Embedding> findByEmpresaId(Long empresaId);

    @Query("SELECT e FROM Embedding e WHERE e.empresaId = :empresaId AND e.entidadeTipo = :entidadeTipo ORDER BY e.dataCriacao DESC")
    List<Embedding> findByEntidadeTipoOrdered(Long empresaId, String entidadeTipo);

    @Query("SELECT COUNT(e) FROM Embedding e WHERE e.empresaId = :empresaId AND e.entidadeTipo = :entidadeTipo")
    long countByEntidadeTipo(Long empresaId, String entidadeTipo);

    @Query("SELECT e FROM Embedding e WHERE e.empresaId = :empresaId AND e.entidadeTipo = :entidadeTipo AND e.embeddingVector <> NULL")
    List<Embedding> findWithVectors(Long empresaId, String entidadeTipo);
}
