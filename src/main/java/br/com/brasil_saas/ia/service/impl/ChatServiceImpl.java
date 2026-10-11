package br.com.brasil_saas.ia.service.impl;

import br.com.brasil_saas.ia.dto.ChatRequest;
import br.com.brasil_saas.ia.dto.ChatResponse;
import br.com.brasil_saas.ia.service.ChatService;
import br.com.brasil_saas.shared.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    @Override
    public ChatResponse sendMessage(ChatRequest request) {
        String resposta = chatClient.prompt()
            .user(request.message())
            .call()
            .content();

        return new ChatResponse(
            null,
            request.message(),
            "default",
            0L,
            0L,
            LocalDateTime.now(),
            Collections.singletonList(new ChatResponse.ChatMessage("assistant", resposta, 0L)),
            true,
            null
        );
    }

    @Override
    public ChatResponse chat(Long empresaId, ChatRequest request) {
        return sendMessage(request);
    }

    @Override
    public ChatResponse startNewSession(String firstMessage, Long userId, Long empresaId) {
        String resposta = chatClient.prompt()
            .user(firstMessage)
            .call()
            .content();
        
        return new ChatResponse(
            null,
            firstMessage,
            "default",
            0L,
            0L,
            LocalDateTime.now(),
            Collections.singletonList(new ChatResponse.ChatMessage("assistant", resposta, 0L)),
            true,
            null
        );
    }

    @Override
    public PageResponse<ChatResponse> listSessions(Long empresaId, Pageable pageable) {
        Page<ChatResponse> page = Page.empty(pageable);
        return PageResponse.from(page, r -> r);
    }

    @Override
    public void deleteSession(Long sessionId, Long empresaId) {
        // Implementação pendente
    }

    @Override
    public ChatResponse getSessionHistory(Long sessionId, Long empresaId) {
        return new ChatResponse(
            sessionId, 
            "", 
            "system", 
            0L, 
            0L, 
            LocalDateTime.now(), 
            Collections.emptyList(), 
            false, 
            "Histórico não implementado"
        );
    }

    @Override
    public String generateText(String prompt, String model, Long empresaId) {
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public String analyzeData(String data, String analysisType, Long empresaId) {
        String prompt = "Analise os seguintes dados do tipo " + analysisType + ": " + data;
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public ChatResponse chatSimples(Long empresaId, Long usuarioId, String mensagem) {
        String resposta = chatClient.prompt()
            .user(mensagem)
            .call()
            .content();
        
        return new ChatResponse(
            null,
            mensagem,
            "default",
            0L,
            0L,
            LocalDateTime.now(),
            Collections.singletonList(new ChatResponse.ChatMessage("assistant", resposta, 0L)),
            true,
            null
        );
    }

    @Override
    public ChatResponse chatComContexto(Long empresaId, Long usuarioId, String mensagem, List<String> contexto) {
        StringBuilder prompt = new StringBuilder();
        if (contexto != null && !contexto.isEmpty()) {
            prompt.append("Contexto: ").append(String.join("\n", contexto)).append("\n\n");
        }
        prompt.append("Pergunta: ").append(mensagem);
        
        String resposta = chatClient.prompt()
            .user(prompt.toString())
            .call()
            .content();
        
        return new ChatResponse(
            null,
            mensagem,
            "default",
            0L,
            0L,
            LocalDateTime.now(),
            Collections.singletonList(new ChatResponse.ChatMessage("assistant", resposta, 0L)),
            true,
            null
        );
    }

    @Override
    public String gerarResposta(Long empresaId, String prompt, String modelo, Double temperatura, Integer maxTokens) {
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public String gerarRespostaComHistorico(Long empresaId, List<String> historico, String mensagem, String modelo, Double temperatura, Integer maxTokens) {
        StringBuilder prompt = new StringBuilder();
        if (historico != null && !historico.isEmpty()) {
            prompt.append("Histórico da conversa:\n");
            for (String msg : historico) {
                prompt.append("- ").append(msg).append("\n");
            }
            prompt.append("\n");
        }
        prompt.append("Última mensagem: ").append(mensagem);
        
        return chatClient.prompt()
            .user(prompt.toString())
            .call()
            .content();
    }

    @Override
    public String resumirTexto(Long empresaId, String texto, Integer maxTokens) {
        String prompt = "Resuma o seguinte texto de forma concisa:\n" + texto;
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public String traduzirTexto(Long empresaId, String texto, String idiomaDestino) {
        String prompt = "Traduza o seguinte texto para " + idiomaDestino + ":\n" + texto;
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public String corrigirTexto(Long empresaId, String texto) {
        String prompt = "Corrija gramática e ortografia do seguinte texto:\n" + texto;
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public String extrairInformacoes(Long empresaId, String texto, String tipoEntidade) {
        String prompt = "Extraia informações do tipo " + tipoEntidade + " do seguinte texto:\n" + texto;
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }
}
