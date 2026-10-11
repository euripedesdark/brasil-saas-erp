package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class IntegrationEnterpriseController {
 private final JdbcTemplate jdbc;

 @GetMapping("/api/enterprise/integracoes/endpoints")
 @PreAuthorize("hasAuthority('integracao:leitura')")
 public List<Map<String,Object>> endpoints(@AuthenticationPrincipal AuthenticatedUser u){
  return jdbc.queryForList("select id,codigo,nome,tipo,url,eventos,ativo,timeout_ms,tentativas,created_at,updated_at from brasil_saas.bc_int_endpoint where empresa_id=? order by id desc",u.getEmpresaId());
 }

 @PostMapping("/api/enterprise/integracoes/endpoints")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 public Map<String,Object> endpoint(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  String secret=String.valueOf(b.getOrDefault("segredo",""));
  jdbc.update("insert into brasil_saas.bc_int_endpoint(empresa_id,codigo,nome,tipo,url,segredo_hash,eventos,ativo,timeout_ms,tentativas) values(?,?,?,?,?,?,?,?,?,?)",
   u.getEmpresaId(),required(b,"codigo"),required(b,"nome"),b.getOrDefault("tipo","WEBHOOK"),required(b,"url"),secret.isBlank()?null:sha256(secret),b.get("eventos"),b.getOrDefault("ativo",true),intValue(b.get("timeoutMs"),10000),intValue(b.get("tentativas"),3));
  return Map.of("ok",true);
 }

 @PostMapping("/api/enterprise/integracoes/eventos")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 public Map<String,Object> publicar(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  String type=required(b,"eventType"), payload=String.valueOf(b.getOrDefault("payload","{}"));
  jdbc.update("insert into brasil_saas.bc_core_integration_event(event_type,source_system,aggregate_type,aggregate_id,payload,status,correlation_id,created_at) values(?,?,?,?,?,'PENDING',?,now())",
   type,"erp",b.get("aggregateType"),longValue(b.get("aggregateId")),payload,UUID.randomUUID());
  int n=jdbc.update("insert into brasil_saas.bc_int_delivery(empresa_id,endpoint_id,event_type,aggregate_type,aggregate_id,payload,status,correlation_id) select ?,id,?,?,?,?, 'PENDENTE',? from brasil_saas.bc_int_endpoint where empresa_id=? and ativo=true and (eventos is null or eventos='' or eventos like ?)",
   u.getEmpresaId(),type,b.get("aggregateType"),longValue(b.get("aggregateId")),payload,UUID.randomUUID(),u.getEmpresaId(),"%"+type+"%");
  return Map.of("ok",true,"deliveries",n);
 }

 @GetMapping("/api/enterprise/integracoes/entregas")
 @PreAuthorize("hasAuthority('integracao:leitura')")
 public List<Map<String,Object>> entregas(@AuthenticationPrincipal AuthenticatedUser u){
  return jdbc.queryForList("select * from brasil_saas.bc_int_delivery where empresa_id=? order by id desc limit 500",u.getEmpresaId());
 }

 @PostMapping("/api/enterprise/integracoes/entregas/{id}/retry")
 @PreAuthorize("hasAuthority('integracao:escrita')")
 public Map<String,Object> retry(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  int n=jdbc.update("update brasil_saas.bc_int_delivery set status='PENDENTE',next_attempt_at=now(),last_error=null where id=? and empresa_id=?",id,u.getEmpresaId());
  if(n==0) throw new NoSuchElementException("Entrega não encontrada");
  return Map.of("ok",true);
 }

 @PostMapping("/api/integracoes/webhook/{empresaId}/{codigo}")
 public Map<String,Object> webhook(@PathVariable Long empresaId,@PathVariable String codigo,@RequestHeader(value="X-Integration-Secret",required=false) String secret,@RequestHeader(value="X-Event-Id",required=false) String eventId,@RequestHeader(value="X-Event-Type",required=false) String eventType,@RequestBody String payload){
  Map<String,Object> ep=jdbc.queryForMap("select id,segredo_hash from brasil_saas.bc_int_endpoint where empresa_id=? and codigo=? and ativo=true",empresaId,codigo);
  String expected=(String)ep.get("segredo_hash");
  if(expected!=null && !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),sha256(secret==null?"":secret).getBytes(StandardCharsets.UTF_8))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Webhook inválido");
  String eid=eventId==null||eventId.isBlank()?UUID.randomUUID().toString():eventId;
  try {
   jdbc.update("insert into brasil_saas.bc_int_webhook_event(empresa_id,endpoint_id,event_id,event_type,payload,signature,status) values(?,?,?,?,?,?,'RECEBIDO')",empresaId,ep.get("id"),eid,eventType==null?"generic":eventType,payload,secret);
  } catch(org.springframework.dao.DuplicateKeyException e){ return Map.of("ok",true,"duplicate",true,"eventId",eid); }
  return Map.of("ok",true,"eventId",eid);
 }

 private static String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new IllegalArgumentException(k+" é obrigatório");return v.toString();}
 private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
 private static int intValue(Object v,int d){return v==null?d:Integer.parseInt(v.toString());}
 private static String sha256(String v){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}