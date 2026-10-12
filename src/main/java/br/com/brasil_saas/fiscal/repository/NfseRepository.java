package br.com.brasil_saas.fiscal.repository;

import br.com.brasil_saas.fiscal.model.Nfse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface NfseRepository extends JpaRepository<Nfse, Long> {
    @Query("select n from Nfse n where n.empresaId = :empresaId and n.dataEmissao >= :de and n.dataEmissao < :ate and n.deletedAt is null")
    java.util.List<Nfse> findNoPeriodo(@Param("empresaId") Long empresaId, @Param("de") java.time.LocalDateTime de, @Param("ate") java.time.LocalDateTime ate);
    Optional<Nfse> findByEmpresaIdAndCodigoVerificacao(Long empresaId, String codigoVerificacao);

    java.util.List<Nfse> findAllByEmpresaIdAndDeletedAtIsNullOrderByDataEmissaoDesc(Long empresaId);

    /**
     * Busca a nota pelo id <b>e</b> pela empresa.
     *
     * <p>Existe porque o id sozinho nao protege nada. Os ids sao sequenciais e
     * previsiveis, entao um ADMIN de uma empresa que adivinhe o id da nota de
     * outra consegue baixar o XML, o PDF, ler o retorno da prefeitura e
     * cancelar a nota. O filtro por tenant na query — e nao depois, no Java — e
     * o que impede: se o id for de outra empresa, a query nao acha.
     */
    Optional<Nfse> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);
    java.util.Optional<Nfse> findTop1ByEmpresaIdOrderByIdDesc(Long empresaId);
    java.util.List<Nfse> findByEmpresaIdAndDataEmissaoBetweenAndDeletedAtIsNull(Long empresaId, java.time.LocalDateTime de, java.time.LocalDateTime ate);

    @Query("select count(n) from Nfse n where n.empresaId = :empresaId and n.dataEmissao >= :de and n.dataEmissao < :ate and n.deletedAt is null")
    long countNoPeriodo(@Param("empresaId") Long empresaId, @Param("de") java.time.LocalDateTime de, @Param("ate") java.time.LocalDateTime ate);
}
