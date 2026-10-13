package br.com.brasil_saas.ia.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Fornece os beans de IA ({@link ChatModel} e {@link ChatClient}) usados por
 * ChatServiceImpl.
 *
 * O autoconfigure do Spring AI não expõe um ChatClient por padrão, e o
 * ChatModel do OpenAI fica desabilitado quando spring.ai.model.chat=none
 * (caso do perfil de teste). Sem estes beans o contexto da aplicação falha ao
 * subir (NoSuchBeanDefinitionException).
 *
 * O fallback abaixo NÃO faz chamadas externas: apenas registra um log de aviso
 * e devolve mensagem vazia. Em produção, com spring.ai.model.chat=openai,
 * o autoconfigure fornece o OpenAiChatModel real e o @ConditionalOnMissingBean
 * impede este fallback de ser criado — comportamento inalterado.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "none", matchIfMissing = true)
    public ChatModel fallbackChatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                org.slf4j.LoggerFactory.getLogger(ChatClientConfig.class)
                        .warn("IA indisponível neste ambiente (spring.ai.model.chat=none); "
                                + "resposta de fallback sem chamada externa.");
                return new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ChatOptions.builder().build();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
