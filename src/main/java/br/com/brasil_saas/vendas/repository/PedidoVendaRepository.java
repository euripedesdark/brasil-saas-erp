package br.com.brasil_saas.vendas.repository;

import br.com.brasil_saas.vendas.model.PedidoVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PedidoVendaRepository extends JpaRepository<PedidoVenda, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PedidoVenda p WHERE p.id = :id")
    Optional<PedidoVenda> findByIdForUpdate(Long id);

    /**
     * Mesma trava, porem ja restrita a empresa.
     *
     * Sem empresa no filtro, confirmar/faturar/cancelar por id tocavam pedido
     * de qualquer empresa. Filtrar tambem na consulta evita o caso em que o
     * pedido era carregado e so depois rejeitado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PedidoVenda p WHERE p.id = :id AND p.empresaId = :empresaId")
    Optional<PedidoVenda> findByIdForUpdateAndEmpresaId(Long id, Long empresaId);

    Optional<PedidoVenda> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Busca pedido pelo número (campo de negócio) da empresa.
     * Evita carregar toda a lista de pedidos em memória só para localizar um número.
     */
    Optional<PedidoVenda> findByEmpresaIdAndNumero(Long empresaId, String numero);

    List<PedidoVenda> findByEmpresaIdOrderByDataEmissaoDesc(Long empresaId);

    List<PedidoVenda> findByEmpresaIdAndStatus(Long empresaId, String status);

    List<PedidoVenda> findByEmpresaIdAndClienteId(Long empresaId, Long clienteId);

    @Query("SELECT p FROM PedidoVenda p WHERE p.empresaId = :empresaId " +
           "AND p.dataEmissao BETWEEN :inicio AND :fim ORDER BY p.dataEmissao DESC")
    List<PedidoVenda> findByEmpresaIdAndPeriodo(Long empresaId, LocalDate inicio, LocalDate fim);

    List<PedidoVenda> findByEmpresaIdAndStatusAndDataEmissaoLessThanEqual(
        Long empresaId, String status, LocalDate data);
}
