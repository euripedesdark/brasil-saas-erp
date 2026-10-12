package br.com.brasil_saas.cadastro.repository;

import br.com.brasil_saas.cadastro.model.Pessoa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, Long> {

    Optional<Pessoa> findByUuidAndEmpresaIdAndDeletedAtIsNull(UUID uuid, Long empresaId);

    Page<Pessoa> findByEmpresaIdAndDeletedAtIsNull(Long empresaId, Pageable pageable);

    @Query("SELECT p FROM Pessoa p WHERE p.empresaId = :empresaId AND p.deletedAt IS NULL AND " +
           "(:nome IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:nome AS String), '%'))) AND " +
           "(:documento IS NULL OR p.documento = :documento)")
    Page<Pessoa> buscar(@Param("empresaId") Long empresaId, @Param("nome") String nome,
                        @Param("documento") String documento, Pageable pageable);

    boolean existsByEmpresaIdAndDocumentoAndDeletedAtIsNull(Long empresaId, String documento);

    /**
     * Acha a pessoa pelo CNPJ/CPF, para casar o emitente da nota com um
     * fornecedor ja cadastrado.
     *
     * <p>O documento e guardado so com digitos (14375732000170, e nao
     * 14.375.732/0001-70), que e exatamente o formato que o XML traz, entao
     * nao ha normalizacao a fazer aqui. O emissor da nota e sempre pessoa
     * juridica com CNPJ; o destinatario pode ser CPF, por isso a nota de
     * compra tem fornecedor e a de venda tem cliente.
     */
    Optional<Pessoa> findByEmpresaIdAndDocumentoAndDeletedAtIsNull(Long empresaId, String documento);

    boolean existsByEmpresaIdAndDocumentoAndIdNotAndDeletedAtIsNull(Long empresaId, String documento, Long id);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}
