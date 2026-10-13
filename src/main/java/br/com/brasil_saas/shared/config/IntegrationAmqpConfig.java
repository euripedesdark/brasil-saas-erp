package br.com.brasil_saas.shared.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fundacao do Hub: exchange topico + fila unica para o futuro consumidor
 * (Astral). JSON no fio para qualquer linguagem ler. Nada de Hub aqui.
 */
@Configuration
public class IntegrationAmqpConfig {

    public static final String EXCHANGE = "br.saas.integration";
    public static final String QUEUE = "br.saas.integration.events";

    @Bean
    public TopicExchange integrationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue integrationQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding integrationBinding(Queue integrationQueue, TopicExchange integrationExchange) {
        return BindingBuilder.bind(integrationQueue).to(integrationExchange).with("#");
    }

    @Bean
    public MessageConverter integrationMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
