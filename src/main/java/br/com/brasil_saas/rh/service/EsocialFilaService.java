package br.com.brasil_saas.rh.service;

import br.com.brasil_saas.rh.model.EsocialEvento;
import br.com.brasil_saas.rh.repository.EsocialEventoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fila eSocial ERP → microserviço esocial-jt (src/main/resources/microservices/esocial).
 * Contrato: POST /ocorrencias com OcorrenciaDTO (tipoOcorrencia, referencia, operacao, dadosOcorrencia).
 */
@Service
@RequiredArgsConstructor
public class EsocialFilaService {

    private final EsocialEventoRepository repo;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, String> TIPO_JT = Map.ofEntries(
            Map.entry("S-1000", "INFORMACOES_EMPREGADOR"),
            Map.entry("S-1005", "TABELA_ESTABELECIMENTO"),
            Map.entry("S-1010", "TABELA_RUBRICA"),
            Map.entry("S-1020", "TABELA_LOTACAO"),
            Map.entry("S-1070", "TABELA_PROCESSO"),
            Map.entry("S-1200", "REMUNERACAO_RGPS"),
            Map.entry("S-1202", "REMUNERACAO_RPPS"),
            Map.entry("S-1207", "BENEFICIO_RPPS"),
            Map.entry("S-1210", "PAGAMENTOS"),
            Map.entry("S-1298", "REABERTURA_PERIODICOS"),
            Map.entry("S-1299", "FECHAMENTO_PERIODICOS"),
            Map.entry("S-2200", "ADMISSAO_TRABALHADOR"),
            Map.entry("S-2205", "ALTERACAO_CADASTRAL"),
            Map.entry("S-2206", "ALTERACAO_CONTRATUAL"),
            Map.entry("S-2210", "CAT"),
            Map.entry("S-2220", "MONIT"),
            Map.entry("S-2230", "AFASTAMENTO_TEMPORARIO"),
            Map.entry("S-2231", "CESSAO"),
            Map.entry("S-2298", "REINTEGRACAO"),
            Map.entry("S-2299", "DESLIGAMENTO"),
            Map.entry("S-2300", "TSV_INICIO"),
            Map.entry("S-2306", "TSV_ALTERACAO_CONTRATUAL"),
            Map.entry("S-2399", "TSV_TERMINO"),
            Map.entry("S-3000", "EXCLUSAO")
    );

    private String baseUrl() {
        String v = System.getenv("ESOCIAL_URL");
        return (v == null || v.isBlank()) ? "http://localhost:8081" : v.replaceAll("/$", "");
    }

    private <T> T exigir(Optional<T> o, String msg) {
        return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg));
    }

    public List<EsocialEvento> listar(Long empresaId, String status) {
        if (status == null || status.isBlank()) {
            return repo.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresaId);
        }
        return repo.findByEmpresaIdAndStatusAndDeletedAtIsNull(empresaId, status);
    }

    public Map<String, String> tiposSuportados() {
        return new LinkedHashMap<>(TIPO_JT);
    }

    @Transactional
    public EsocialEvento registrar(Long empresaId, String tipo, Long funcionarioId, String payload) {
        String t = tipo == null ? "" : tipo.trim().toUpperCase();
        if (!TIPO_JT.containsKey(t)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tipo invalido: " + tipo + ". Use um dos: " + TIPO_JT.keySet());
        }
        EsocialEvento e = new EsocialEvento();
        e.setEmpresaId(empresaId);
        e.setTipo(t);
        e.setFuncionarioId(funcionarioId);
        e.setPayload(payload);
        e.setStatus("PENDENTE");
        return repo.save(e);
    }

    @Transactional
    public EsocialEvento transmitir(Long empresaId, Long id) {
        EsocialEvento e = exigir(repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Evento inexistente");
        if ("ENVIADO".equals(e.getStatus()) || "PROCESSADO".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Evento ja transmitido (status=" + e.getStatus() + ")");
        }
        String tipoJt = TIPO_JT.get(e.getTipo());
        if (tipoJt == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo nao mapeado: " + e.getTipo());
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("tipoOcorrencia", tipoJt);
        corpo.put("referencia", e.getFuncionarioId() != null ? "FUNC-" + e.getFuncionarioId() : "EVT-" + e.getId());
        corpo.put("operacao", "NORMAL");
        corpo.put("dataOcorrencia", Date.from(Instant.now()));
        corpo.put("dadosOcorrencia", parseDados(e.getPayload()));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> resp = rest.exchange(
                    baseUrl() + "/ocorrencias",
                    HttpMethod.POST,
                    new HttpEntity<>(corpo, headers),
                    Map.class);
            Map<?, ?> body = resp.getBody();
            e.setStatus("ENVIADO");
            e.setErro(null);
            if (body != null && body.get("id") != null) {
                e.setProtocolo("JT-" + body.get("id"));
            }
            if (body != null && body.get("evento") instanceof Map<?, ?> ev) {
                if (ev.get("nrRecibo") != null) e.setRecibo(String.valueOf(ev.get("nrRecibo")));
            }
        } catch (RestClientResponseException ex) {
            e.setStatus("FALHA");
            String body = ex.getResponseBodyAsString();
            String msg = (body != null && !body.isBlank()) ? body : ex.getMessage();
            if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
            e.setErro(trim(msg, 2000));
        } catch (Exception ex) {
            e.setStatus("FALHA");
            String msg = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            e.setErro(trim(msg, 2000));
        }
        return repo.save(e);
    }

    @Transactional
    public EsocialEvento consultar(Long empresaId, Long id) {
        EsocialEvento e = exigir(repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Evento inexistente");
        String proto = e.getProtocolo();
        if (proto == null || !proto.startsWith("JT-")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Sem id JT no protocolo — transmita antes");
        }
        try {
            Map<?, ?> body = rest.getForObject(baseUrl() + "/ocorrencias/" + proto.substring(3), Map.class);
            if (body != null && body.get("evento") instanceof Map<?, ?> ev) {
                Object estado = ev.get("estado");
                String desc = null;
                if (estado instanceof Map<?, ?> st) {
                    desc = st.get("descricao") != null ? String.valueOf(st.get("descricao")) : null;
                } else if (estado != null) {
                    desc = String.valueOf(estado);
                }
                if (desc != null) {
                    e.setRecibo(trim(desc, 200));
                    String d = desc.toUpperCase();
                    if (d.contains("PROCESSADO") || d.contains("SUCESSO")) e.setStatus("PROCESSADO");
                    else if (d.contains("ERRO") || d.contains("RECUS")) e.setStatus("RECUSADO");
                }
                if (ev.get("nrRecibo") != null) e.setRecibo(String.valueOf(ev.get("nrRecibo")));
            }
            e.setErro(null);
        } catch (RestClientResponseException ex) {
            e.setErro(trim(ex.getResponseBodyAsString(), 2000));
        } catch (Exception ex) {
            e.setErro(trim(ex.getMessage(), 2000));
        }
        return repo.save(e);
    }

    @Transactional
    public void excluir(Long empresaId, Long id) {
        EsocialEvento e = exigir(repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId), "Evento inexistente");
        if ("ENVIADO".equals(e.getStatus()) || "PROCESSADO".equals(e.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Evento ja enviado");
        }
        repo.delete(e);
    }

    private Object parseDados(String payload) {
        if (payload == null || payload.isBlank()) return Map.of();
        String p = payload.trim();
        try {
            if (p.startsWith("{") || p.startsWith("[")) {
                return mapper.readValue(p, new TypeReference<Object>() {});
            }
        } catch (Exception ignored) { }
        return Map.of("texto", p);
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
