package br.com.brasil_saas.cadastro.repository;

import br.com.brasil_saas.cadastro.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id and c.empresaId = :empresaId and c.deletedAt is null")
    Optional<Cliente> findForUpdate(@Param("id") Long id, @Param("empresaId") Long empresaId);
    Page<Cliente> findByDeletedAtIsNull(Pageable pageable);
    Page<Cliente> findByEmpresaIdAndDeletedAtIsNull(Long empresaId, Pageable pageable);
    Optional<Cliente> findByPessoaIdAndDeletedAtIsNull(Long pessoaId);
    Optional<Cliente> findByEmpresaIdAndPessoaIdAndDeletedAtIsNull(Long empresaId, Long pessoaId);
    Optional<Cliente> findByIdAndEmpresaIdAndDeletedAtIsNull(Long id, Long empresaId);

    @Query("select c from Cliente c join fetch c.pessoa p where c.id = :id and c.empresaId = :empresaId and c.deletedAt is null")
    Optional<Cliente> findByIdAndEmpresaIdAndDeletedAtIsNullWithPessoa(@Param("id") Long id, @Param("empresaId") Long empresaId);

    boolean existsByPessoaIdAndDeletedAtIsNull(Long pessoaId);

    @Query("select p.nome from Cliente c join c.pessoa p where c.id = :id and c.deletedAt is null")
    Optional<String> nomeDaPessoaPorClienteId(@Param("id") Long id);
    long countByEmpresaIdAndDeletedAtIsNull(Long empresaId);
}