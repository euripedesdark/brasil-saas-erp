package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.enterprise.model.IntDelivery;
import br.com.brasil_saas.enterprise.model.IntEndpoint;
import br.com.brasil_saas.enterprise.model.IntWebhookEvent;
import br.com.brasil_saas.enterprise.repository.IntDeliveryRepository;
import br.com.brasil_saas.enterprise.repository.IntEndpointRepository;
import br.com.brasil_saas.enterprise.repository.IntWebhookEventRepository;
import br.com.brasil_saas.shared.model.IntegrationEvent;
import br.com.brasil_saas.shared.repository.IntegrationEventRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class IntegrationEnterpriseController {
 private final IntEndpointRepository endpointRepository;
 private final IntDeliveryRepository deliveryRepository;
 private final IntWebhookEventRepository webhookEventRepository;
 private final IntegrationEventRepository eventRepository;

 @GetMapping("/api/enterprise/integracoes/endpoints")
 @PreAuthorize("hasAuthority('integracao:leitura')")
 public List<Map<String,Object>> endpoints(@AuthenticationPrincipal AuthenticatedUser u){
  return endpointRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId(), PageRequest.of(0, 500))
    .stream().map(e -> {
      Map<String,Object> m = new LinkedHashMap<>();
      m.put("id", e.getId()); m.put("codigo", e.getCodigo()); m.put("nome", e.getNome());
      m.put("tipo", e.getTipo()); m.put("url", e.getUrl()); m.put("eventos", e.getEventos());
      m.put("ativo", e.getAtivo()); m.put("timeout_ms", e.getTimeoutMs()); m.put("tentativas", e.getTentativas());
      m.put("created_at", e.getCreatedAt()); m.put("updated_at", e.getUpdatedAt());
      return m;
    }).toList();
 }

 @PostMapping("/api/enterprise/integracoes/endpoints")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 @Transactional
 public Map<String,Object> endpoint(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  String secret=String.valueOf(b.getOrDefault("segredo",""));
  IntEndpoint e = new IntEndpoint();
  e.setEmpresaId(u.getEmpresaId());
  e.setCodigo(required(b,"codigo"));
  e.setNome(required(b,"nome"));
  e.setTipo(String.valueOf(b.getOrDefault("tipo","WEBHOOK")));
  e.setUrl(required(b,"url"));
  e.setSegredoHash(secret.isBlank()?null:sha256(secret));
  e.setEventos(b.get("eventos") == null ? null : String.valueOf(b.get("eventos")));
  e.setAtivo(boolValue(b.getOrDefault("ativo",true), true));
  e.setTimeoutMs(intValue(b.get("timeoutMs"),10000));
  e.setTentativas(intValue(b.get("tentativas"),3));
  endpointRepository.save(e);
  return Map.of("ok",true);
 }

 @PostMapping("/api/enterprise/integracoes/eventos")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 @Transactional
 public Map<String,Object> publicar(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  String type=required(b,"eventType"), payload=String.valueOf(b.getOrDefault("payload","{}"));
  Long aggregateId = longValue(b.get("aggregateId"));
  String aggregateType = b.get("aggregateType") == null ? null : String.valueOf(b.get("aggregateType"));
  IntegrationEvent e = new IntegrationEvent();
  e.setEventType(type);
  e.setSourceSystem("erp");
  e.setAggregateType(aggregateType);
  e.setAggregateId(aggregateId);
  e.setPayload(payload);
  e.setStatus(IntegrationEvent.PENDING);
  e.setCorrelationId(UUID.randomUUID());
  eventRepository.save(e);
  UUID correlation = UUID.randomUUID();
  List<IntDelivery> deliveries = new ArrayList<>();
  for (IntEndpoint ep : endpointRepository.findByEmpresaIdAndAtivoTrue(u.getEmpresaId())) {
    String eventos = ep.getEventos();
    if (eventos != null && !eventos.isBlank() && !eventos.contains(type)) continue;
    IntDelivery d = new IntDelivery();
    d.setEmpresaId(u.getEmpresaId());
    d.setEndpointId(ep.getId());
    d.setEventType(type);
    d.setAggregateType(aggregateType);
    d.setAggregateId(aggregateId);
    d.setPayload(payload);
    d.setStatus("PENDENTE");
    d.setCorrelationId(correlation);
    deliveries.add(d);
  }
  deliveryRepository.saveAll(deliveries);
  return Map.of("ok",true,"deliveries",deliveries.size());
 }

 @GetMapping("/api/enterprise/integracoes/entregas")
 @PreAuthorize("hasAuthority('integracao:leitura')")
 public List<Map<String,Object>> entregas(@AuthenticationPrincipal AuthenticatedUser u){
  return deliveryRepository.findByEmpresaIdOrderByIdDesc(u.getEmpresaId(), PageRequest.of(0, 500))
    .stream().map(IntegrationEnterpriseController::deliveryMap).toList();
 }

 @PostMapping("/api/enterprise/integracoes/entregas/{id}/retry")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 @Transactional
 public Map<String,Object> retry(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  IntDelivery d = deliveryRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Entrega não encontrada"));
  d.setStatus("PENDENTE");
  d.setNextAttemptAt(LocalDateTime.now());
  d.setLastError(null);
  deliveryRepository.save(d);
  return Map.of("ok",true);
 }

 @PostMapping("/api/integracoes/webhook/{empresaId}/{codigo}")
 @Transactional
 public Map<String,Object> webhook(@PathVariable Long empresaId,@PathVariable String codigo,@RequestHeader(value="X-Integration-Secret",required=false) String secret,@RequestHeader(value="X-Event-Id",required=false) String eventId,@RequestHeader(value="X-Event-Type",required=false) String eventType,@RequestBody String payload){
  IntEndpoint ep = endpointRepository.findByEmpresaIdAndCodigoAndAtivoTrue(empresaId,codigo)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Webhook inválido"));
  String expected=ep.getSegredoHash();
  if(expected!=null && !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),sha256(secret==null?"":secret).getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Webhook inválido");
  String eid=eventId==null||eventId.isBlank()?UUID.randomUUID().toString():eventId;
  try {
   IntWebhookEvent w = new IntWebhookEvent();
   w.setEmpresaId(empresaId);
   w.setEndpointId(ep.getId());
   w.setEventId(eid);
   w.setEventType(eventType==null?"generic":eventType);
   w.setPayload(payload);
   w.setSignature(secret);
   w.setStatus("RECEBIDO");
   webhookEventRepository.saveAndFlush(w);
  } catch(DataIntegrityViolationException e){ return Map.of("ok",true,"duplicate",true,"eventId",eid); }
  return Map.of("ok",true,"eventId",eid);
 }

 private static Map<String,Object> deliveryMap(IntDelivery d) {
   Map<String,Object> m = new LinkedHashMap<>();
   m.put("id", d.getId()); m.put("empresa_id", d.getEmpresaId()); m.put("endpoint_id", d.getEndpointId());
   m.put("event_type", d.getEventType()); m.put("aggregate_type", d.getAggregateType());
   m.put("aggregate_id", d.getAggregateId()); m.put("payload", d.getPayload());
   m.put("status", d.getStatus()); m.put("attempts", d.getAttempts());
   m.put("next_attempt_at", d.getNextAttemptAt()); m.put("last_error", d.getLastError());
   m.put("delivered_at", d.getDeliveredAt()); m.put("correlation_id", d.getCorrelationId());
   m.put("created_at", d.getCreatedAt());
   return m;
 }

 private static String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new IllegalArgumentException(k+" é obrigatório");return v.toString();}
 private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
 private static int intValue(Object v,int d){return v==null?d:Integer.parseInt(v.toString());}
 private static Boolean boolValue(Object v, boolean d){return v==null?d:(v instanceof Boolean b?b:Boolean.parseBoolean(v.toString()));}
 private static String sha256(String v){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
