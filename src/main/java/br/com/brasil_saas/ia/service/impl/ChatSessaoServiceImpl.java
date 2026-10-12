package br.com.brasil_saas.ia.service.impl;

import br.com.brasil_saas.ia.dto.ChatSessaoRequest;
import br.com.brasil_saas.ia.model.ChatMensagem;
import br.com.brasil_saas.ia.model.ChatSessao;
import br.com.brasil_saas.ia.repository.ChatMensagemRepository;
import br.com.brasil_saas.ia.repository.ChatSessaoRepository;
import br.com.brasil_saas.ia.service.ChatSessaoService;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessaoServiceImpl implements ChatSessaoService {

    private final ChatSessaoRepository sessaoRepository;
    private final ChatMensagemRepository mensagemRepository;

    @Override
    @Transactional
    public ChatSessao criar(Long empresaId, ChatSessaoRequest request) {
        ChatSessao sessao = new ChatSessao();
        sessao.setEmpresaId(empresaId);
        sessao.setTitulo(request.titulo());
        sessao.setUsuarioId(request.usuarioId());
        sessao.setModeloIa(request.modeloIa());
        sessao.setTemperatura(request.temperatura());
        sessao.setMaxTokens(request.maxTokens());
        sessao.setAtivo(request.ativo());
        sessao.setFavorito(request.favorito());
        sessao.setDataCriacao(LocalDateTime.now());
        sessao.setDataAtualizacao(LocalDateTime.now());

        return sessaoRepository.save(sessao);
    }

    @Override
    @Transactional
    public ChatSessao atualizar(Long empresaId, Long id, ChatSessaoRequest request) {
        ChatSessao sessao = buscarDaEmpresa(empresaId, id);

        sessao.setTitulo(request.titulo());
        sessao.setModeloIa(request.modeloIa());
        sessao.setTemperatura(request.temperatura());
        sessao.setMaxTokens(request.maxTokens());
        sessao.setAtivo(request.ativo());
        sessao.setFavorito(request.favorito());
        sessao.setDataAtualizacao(LocalDateTime.now());

        return sessaoRepository.save(sessao);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatSessao buscarPorId(Long empresaId, Long id) {
        return buscarDaEmpresa(empresaId, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessao> listarPorEmpresa(Long empresaId) {
        return sessaoRepository.findByEmpresaIdAndAtivoTrue(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessao> listarPorUsuario(Long empresaId, Long usuarioId) {
        return sessaoRepository.findByEmpresaIdAndUsuarioId(empresaId, usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessao> listarFavoritos(Long empresaId, Long usuarioId) {
        return sessaoRepository.findByEmpresaIdAndFavoritoTrue(empresaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatSessao> listarRecentes(Long empresaId, Long usuarioId) {
        return sessaoRepository.findRecentByUsuario(empresaId, usuarioId);
    }

    @Override
    @Transactional
    public void excluir(Long empresaId, Long id) {
        ChatSessao sessao = buscarDaEmpresa(empresaId, id);

        // Excluir mensagens da sessao
        List<ChatMensagem> mensagens = mensagemRepository.findByEmpresaIdAndSessaoId(empresaId, id);
        mensagemRepository.deleteAll(mensagens);

        sessaoRepository.delete(sessao);
    }

    @Override
    @Transactional
    public void alternarFavorito(Long empresaId, Long id) {
        ChatSessao sessao = buscarDaEmpresa(empresaId, id);

        sessao.setFavorito(!sessao.getFavorito());
        sessaoRepository.save(sessao);
    }

    @Override
    @Transactional
    public void limparHistorico(Long empresaId, Long id) {
        ChatSessao sessao = buscarDaEmpresa(empresaId, id);

        List<ChatMensagem> mensagens = mensagemRepository.findByEmpresaIdAndSessaoId(empresaId, id);
        mensagemRepository.deleteAll(mensagens);

        sessao.setDataAtualizacao(LocalDateTime.now());
        sessaoRepository.save(sessao);
    }

    /** Busca a sessao garantindo que ela pertence a empresa informada. */
    private ChatSessao buscarDaEmpresa(Long empresaId, Long id) {
        ChatSessao sessao = sessaoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sessao de chat nao encontrada"));

        if (!sessao.getEmpresaId().equals(empresaId)) {
            throw new ResourceNotFoundException("Sessao nao pertence a esta empresa");
        }

        return sessao;
    }
}
