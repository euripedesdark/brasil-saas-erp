package br.com.brasil_saas.core.repository;

import br.com.brasil_saas.core.model.Modulo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    List<Modulo> findByAtivoTrueOrderByOrdemAscNomeAsc();

    java.util.Optional<Modulo> findByChave(String chave);

    /** Modulos liberados para um usuario. */
    @Query("""
            select m from Modulo m
              join UsuarioModulo um on um.moduloId = m.id
             where um.usuarioId = :usuarioId
             order by m.ordem asc
            """)
    List<Modulo> findByUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Os modulos do usuario com a marca de somente-leitura, em uma consulta.
     *
     * <p>O {@code eSomenteLeitura} anterior fazia N+1: uma consulta para os
     * vinculos e uma para cada modulo, dentro de um filtro que roda em TODA
     * requisicao. Com 4 modulos eram 5 consultas por request so para responder
     * "este modulo e' somente leitura".
     *
     * <p>Esta devolve o par pronto, e o filtro decide em memoria.
     */
    @Query("""
            select m.chave, um.somenteLeitura from UsuarioModulo um
              join Modulo m on m.id = um.moduloId
             where um.usuarioId = :usuarioId
            """)
    List<Object[]> modulosEOmenteLeituraDoUsuario(@Param("usuarioId") Long usuarioId);

    /** Verifica se o usuario tem o modulo. */
    @Query("""
            select case when count(um) > 0 then true else false end
              from UsuarioModulo um
             where um.usuarioId = :usuarioId and um.moduloId = :moduloId
            """)
    boolean usuarioTemModulo(@Param("usuarioId") Long usuarioId, @Param("moduloId") Long moduloId);
}
