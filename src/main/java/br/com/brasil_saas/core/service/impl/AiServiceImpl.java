package br.com.brasil_saas.core.service.impl;

import br.com.brasil_saas.core.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;

@Service
@Slf4j
public class AiServiceImpl implements AiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private final String API_KEY = "YOUR_CLAUDE_API_KEY"; // Deve vir de properties

    @Override
    public String translateNL2SQL(String naturalLanguage, String schemaContext) {
        log.info("Traduzindo NL para SQL: {}", naturalLanguage);

        String prompt = String.format(
            "Você é um especialista em PostgreSQL. Converta a seguinte pergunta em uma query SQL válida.\n" +
            "Contexto do Schema:\n%s\n\n" +
            "Pergunta: %s\n\n" +
            "Retorne APENAS a query SQL, sem markdown, sem explicações.",
            schemaContext, naturalLanguage
        );

        try {
            // Simulação de chamada API Claude (ou implementando a chamada real)
            // return callClaudeApi(prompt);

            // Para fins de demonstração/teste, simulamos a resposta:
            if (naturalLanguage.contains("clientes")) {
                return "SELECT count(*) FROM cliente;";
            }
            return "SELECT * FROM (SELECT 'Erro: Não foi possível traduzir' as result) as t;";
        } catch (Exception e) {
            log.error("Erro ao chamar IA: {}", e.getMessage());
            return "SELECT 'Erro ao gerar query' as error;";
        }
    }

    @Override
    public String explainResult(String query, String result) {
        log.info("Explicando resultado da query: {}", query);
        return "O resultado da consulta indica que " + result + ".";
    }

    private String callClaudeApi(String prompt) {
        // Implementação real da API do Claude
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", API_KEY);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new HashMap<>();
        body.put("model", "claude-3-haiku-20240307");
        body.put("max_tokens", 1024);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(CLAUDE_API_URL, entity, Map.class);

        // Parse do resultado do Claude
        Map<?, ?> resposta = response.getBody();
        if (resposta == null || !(resposta.get("content") instanceof List<?> blocos) || blocos.isEmpty()) {
            throw new IllegalStateException("Claude retornou uma resposta vazia");
        }
        if (!(blocos.getFirst() instanceof Map<?, ?> primeiro) || !(primeiro.get("text") instanceof String texto)) {
            throw new IllegalStateException("Claude retornou conteudo sem texto");
        }
        return texto;
    }
}
