package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/corporativo")
@RequiredArgsConstructor
public class CorporateController {
 private final JdbcTemplate jdbc;
 private final br.com.brasil_saas.enterprise.service.IntercompanyService intercompanyService;
 private static final Map<String,String> TABLES=Map.of(
  "intercompany","bc_fin_intercompany","consolidacoes","bc_fin_consolidacao",
  "riscos","bc_gov_risco","controles","bc_gov_controle");
 @GetMapping("/{resource}") @PreAuthorize("hasAuthority('corporativo:leitura')")
 public List<Map<String,Object>> listar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource){
  String t=table(resource); return jdbc.queryForList("select * from brasil_saas."+t+" where empresa_id=? and "+(t.equals("bc_fin_consolidacao")?"true":"deleted_at is null")+" order by id desc",u.getEmpresaId());
 }
 @PostMapping("/{resource}") @PreAuthorize("hasAuthority('corporativo:escrita')")
 public Map<String,Object> criar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@RequestBody Map<String,Object> body){
  String t=table(resource); Map<String,Object>d=sanitize(body);d.put("empresa_id",u.getEmpresaId());d.put("created_by",u.getId());
  List<String>c=new ArrayList<>(d.keySet());if(c.isEmpty())throw new IllegalArgumentException("Nenhum campo informado");
  return jdbc.queryForMap("insert into brasil_saas."+t+" ("+String.join(",",c)+") values ("+String.join(",",Collections.nCopies(c.size(),"?"))+") returning *",c.stream().map(d::get).toArray());
 }
 @PostMapping("/consolidacoes/{id}/executar") @PreAuthorize("hasAuthority('corporativo:escrita')")
 public Map<String,Object> executar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  int n=jdbc.update("update brasil_saas.bc_fin_consolidacao set status='CONSOLIDADO',consolidado_em=now(),consolidado_por=?,updated_at=now() where empresa_id=? and id=?",u.getId(),u.getEmpresaId(),id);
  if(n==0)throw new NoSuchElementException("Consolidação não encontrada");
  return jdbc.queryForMap("select * from brasil_saas.bc_fin_consolidacao where empresa_id=? and id=?",u.getEmpresaId(),id);
 }
 @PostMapping("/intercompany/{id}/reconciliar") @PreAuthorize("hasAuthority('corporativo:escrita')")
 public Map<String,Object> reconciliar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  return intercompanyService.reconciliar(u.getEmpresaId(),u.getId(),id);
 }
 @GetMapping("/intercompany/eliminacoes") @PreAuthorize("hasAuthority('corporativo:leitura')")
 public Map<String,Object> eliminacoes(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam java.time.LocalDate competencia){
  return Map.of("competencia",competencia,"eliminacoes",intercompanyService.eliminacoes(u.getEmpresaId(),competencia));
 }
 private String table(String r){String t=TABLES.get(r);if(t==null)throw new IllegalArgumentException("Recurso corporativo inválido");return t;}
 private Map<String,Object> sanitize(Map<String,Object>s){Map<String,Object>d=new LinkedHashMap<>();s.forEach((k,v)->{if(k!=null&&k.matches("[a-z][a-z0-9_]*")&&!Set.of("id","uuid","empresa_id","created_by","updated_by","deleted_at").contains(k))d.put(k,v);});return d;}
}
