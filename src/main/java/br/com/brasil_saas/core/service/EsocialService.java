package br.com.brasil_saas.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Collections;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsocialService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ESOCIAL_API_URL = "http://localhost:8081"; // Porta do microserviço eSocial

    public String enviarEvento(String tipoEvento, Map<String, Object> dados) {
        log.info("Enviando evento eSocial: {}", tipoEvento);

        try {
            String url = String.format("%s/api/eventos/enviar/%s", ESOCIAL_API_URL, tipoEvento);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(dados, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("recibo");
            }
            return "Erro: " + response.getStatusCode();
        } catch (Exception e) {
            log.error("Erro ao comunicar com microserviço eSocial: {}", e.getMessage());
            return "Erro de comunicação: " + e.getMessage();
        }
    }

    public List<Map<String, Object>> consultarStatusEventos(Long funcionarioId) {
        log.info("Consultando status eSocial para funcionário: {}", funcionarioId);
        try {
            String url = String.format("%s/api/eventos/status/%d", ESOCIAL_API_URL, funcionarioId);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lista = restTemplate.getForObject(url, List.class);
            return lista != null ? lista : Collections.emptyList();
        } catch (Exception e) {
            log.error("Erro ao consultar status eSocial: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
