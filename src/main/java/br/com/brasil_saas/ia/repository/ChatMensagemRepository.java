package br.com.brasil_saas.ia.repository;

import br.com.brasil_saas.ia.model.ChatMensagem;
import br.com.brasil_saas.shared.repository.TenantRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMensagemRepository extends TenantRepository<ChatMensagem, Long> {

    List<ChatMensagem> findByEmpresaIdAndSessaoId(Long empresaId, Long sessaoId);

    List<ChatMensagem> findByEmpresaIdAndSessaoIdAndTipo(Long empresaId, Long sessaoId, String tipo);

    List<ChatMensagem> findByEmpresaIdAndTipo(Long empresaId, String tipo);

    @Query("SELECT m FROM ChatMensagem m WHERE m.empresaId = :empresaId AND m.sessao.id = :sessaoId ORDER BY m.dataEnvio")
    List<ChatMensagem> findBySessaoOrdered(Long empresaId, Long sessaoId);

    @Query("SELECT COUNT(m) FROM ChatMensagem m WHERE m.empresaId = :empresaId AND m.sessao.id = :sessaoId")
    long countBySessao(Long empresaId, Long sessaoId);

    @Query("SELECT COUNT(m) FROM ChatMensagem m WHERE m.empresaId = :empresaId AND m.tipo = 'ASSISTANT'")
    long countRespostasByEmpresa(Long empresaId);

    @Query("SELECT SUM(m.tokens) FROM ChatMensagem m WHERE m.empresaId = :empresaId AND m.tipo = 'ASSISTANT'")
    Long sumTokensByEmpresa(Long empresaId);
}
