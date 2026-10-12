package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.sql.Date;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class SupplyChainEnterpriseController {
 private final JdbcTemplate jdbc;
 private static final Map<String,String> TABLES=tabelas(
  "reposicao","bc_scm_politica_reposicao","transportes","bc_scm_ordem_transporte",
  "revisoes-produto","bc_plm_produto_revisao","mudancas-engenharia","bc_plm_mudanca",
  "ehs","bc_ehs_ocorrencia","contratos-servico","bc_srv_contrato", "plm-documentos","bc_plm_documento", "plm-efeitos","bc_plm_efeito_mudanca", "plm-aprovacoes","bc_plm_aprovacao", "ehs-riscos","bc_ehs_risco", "ehs-inspecoes","bc_ehs_inspecao", "ehs-acoes","bc_ehs_acao", "ehs-permissoes","bc_ehs_permissao_trabalho"
 );
 @GetMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}")
 @PreAuthorize("isAuthenticated()")
 public List<Map<String,Object>> listar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource){
  String t=table(resource);
  return jdbc.queryForList("select * from brasil_saas."+t+" where empresa_id=? and deleted_at is null order by id desc",u.getEmpresaId());
 }
 @PostMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}")
 @PreAuthorize("isAuthenticated()")
 public Map<String,Object> criar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@RequestBody Map<String,Object> body){
  if("revisoes-produto".equals(resource)){
   Map<String,Object> b=new LinkedHashMap<>(body);
   jdbc.update("insert into brasil_saas.bc_plm_produto_revisao(empresa_id,produto_id,revisao,descricao,status,vigente_desde,vigente_ate,motivo,documento_id,criado_por) values(?,?,?,?,?,?,?,?,?,?)",
    u.getEmpresaId(),b.get("produto_id"),b.get("revisao"),b.get("descricao"),b.getOrDefault("status","EM_DESENVOLVIMENTO"),b.get("vigente_desde"),b.get("vigente_ate"),b.get("motivo"),b.get("documento_id"),u.getId());
   return jdbc.queryForMap("select * from brasil_saas.bc_plm_produto_revisao where empresa_id=? order by id desc limit 1",u.getEmpresaId());
  }
  if("plm-documentos".equals(resource)){
   Map<String,Object> b=new LinkedHashMap<>(body);
   jdbc.update("insert into brasil_saas.bc_plm_documento(empresa_id,revisao_id,mudanca_id,tipo,codigo,versao,nome,localizacao,hash_documento,status,obrigatorio,vigencia_inicio,vigencia_fim) values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
    u.getEmpresaId(),b.get("revisao_id"),b.get("mudanca_id"),b.get("tipo"),b.get("codigo"),b.getOrDefault("versao","1"),b.get("nome"),b.get("localizacao"),b.get("hash_documento"),b.getOrDefault("status","RASCUNHO"),b.getOrDefault("obrigatorio",false),b.get("vigencia_inicio"),b.get("vigencia_fim"));
   return jdbc.queryForMap("select * from brasil_saas.bc_plm_documento where empresa_id=? order by id desc limit 1",u.getEmpresaId());
  }
  String t=table(resource); Map<String,Object> d=sanitize(body); d.put("empresa_id",u.getEmpresaId()); d.put("created_by",u.getId());
  List<String> c=new ArrayList<>(d.keySet()); if(c.isEmpty()) throw new IllegalArgumentException("Nenhum campo informado");
  String sql="insert into brasil_saas."+t+" ("+String.join(",",c)+") values ("+String.join(",",Collections.nCopies(c.size(),"?"))+") returning *";
  return jdbc.queryForMap(sql,c.stream().map(d::get).toArray());
 }
 @PutMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}/{id}")
 @PreAuthorize("isAuthenticated()")
 public Map<String,Object> atualizar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@PathVariable Long id,@RequestBody Map<String,Object> body){
  String t=table(resource); Map<String,Object> d=sanitize(body);
  d.remove("empresa_id"); d.remove("created_by"); d.remove("created_at"); d.remove("id"); d.remove("uuid");
  d.put("updated_at",new Date(System.currentTimeMillis())); d.put("updated_by",u.getId());
  List<String> c=new ArrayList<>(d.keySet()); if(c.isEmpty()) throw new IllegalArgumentException("Nenhum campo informado");
  List<Object> v=c.stream().map(d::get).collect(java.util.stream.Collectors.toList()); v.add(u.getEmpresaId()); v.add(id);
  List<Map<String,Object>> r=jdbc.queryForList("update brasil_saas."+t+" set "+String.join(",",c.stream().map(x->x+"=?").toList())+" where empresa_id=? and id=? and deleted_at is null returning *",v.toArray());
  if(r.isEmpty()) throw new NoSuchElementException("Registro não encontrado"); return r.get(0);
 }
 @DeleteMapping("/{resource:reposicao|transportes|revisoes-produto|mudancas-engenharia|ehs|contratos-servico|plm-documentos|plm-efeitos|plm-aprovacoes|ehs-riscos|ehs-inspecoes|ehs-acoes|ehs-permissoes}/{id}")
 @PreAuthorize("isAuthenticated()")
 public void excluir(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@PathVariable Long id){
  String t=table(resource);
  int n=jdbc.update("update brasil_saas."+t+" set deleted_at=?,updated_at=?,updated_by=? where empresa_id=? and id=?",new Date(System.currentTimeMillis()),new Date(System.currentTimeMillis()),u.getId(),u.getEmpresaId(),id);
  if(n==0) throw new NoSuchElementException("Registro não encontrado");
 }
 @PostMapping("/plm/mudancas/{id}/enviar-aprovacao")
 @PreAuthorize("hasAuthority('plm:escrita')")
 public Map<String,Object> enviarParaAprovacao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  ensure("select count(*) from brasil_saas.bc_plm_mudanca where id=? and empresa_id=? and status in ('ABERTA','EM_DESENVOLVIMENTO')",id,u.getEmpresaId(),"Mudança não está disponível para aprovação");
  int efeitos=java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_plm_efeito_mudanca where mudanca_id=? and empresa_id=?",Integer.class,id,u.getEmpresaId())).orElse(0);
  if(efeitos==0) throw new IllegalStateException("A mudança precisa ter pelo menos um efeito de engenharia antes da aprovação");
  jdbc.update("update brasil_saas.bc_plm_mudanca set status='EM_APROVACAO',updated_at=now() where id=? and empresa_id=?",id,u.getEmpresaId());
  jdbc.update("insert into brasil_saas.bc_plm_aprovacao(empresa_id,mudanca_id,etapa,aprovador_id,decisao,obrigatoria) values(?,?,1,null,'PENDENTE',true) on conflict(empresa_id,mudanca_id,etapa) do nothing",u.getEmpresaId(),id);
  return workflow(u,id);
 }

 @GetMapping("/plm/mudancas/{id}/workflow")
 @PreAuthorize("hasAuthority('plm:leitura')")
 public Map<String,Object> workflow(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  ensure("select count(*) from brasil_saas.bc_plm_mudanca where id=? and empresa_id=?",id,u.getEmpresaId(),"Mudança não encontrada");
  Map<String,Object> r=new LinkedHashMap<>();
  r.put("mudanca",jdbc.queryForMap("select * from brasil_saas.bc_plm_mudanca where id=? and empresa_id=?",id,u.getEmpresaId()));
  r.put("aprovacoes",jdbc.queryForList("select * from brasil_saas.bc_plm_aprovacao where mudanca_id=? and empresa_id=? order by etapa",id,u.getEmpresaId()));
  r.put("efeitos",jdbc.queryForList("select * from brasil_saas.bc_plm_efeito_mudanca where mudanca_id=? and empresa_id=? order by ordem_execucao,id",id,u.getEmpresaId()));
  r.put("documentos",jdbc.queryForList("select * from brasil_saas.bc_plm_documento where mudanca_id=? and empresa_id=? and deleted_at is null order by id desc",id,u.getEmpresaId()));
  return r;
 }

 @PostMapping("/plm/mudancas/{id}/efeitos")
 @PreAuthorize("hasAuthority('plm:escrita')")
 public Map<String,Object> adicionarEfeito(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  ensure("select count(*) from brasil_saas.bc_plm_mudanca where id=? and empresa_id=? and status not in ('IMPLEMENTADA','CANCELADA')",id,u.getEmpresaId(),"Mudança não encontrada ou já encerrada");
  String tipo=String.valueOf(b.getOrDefault("entidadeTipo",""));
  String acao=String.valueOf(b.getOrDefault("acao",""));
  Long entidade=longValue(b.get("entidadeId"));
  validarEfeito(u,tipo,acao,entidade);
  Integer ordem=integerValue(b.get("ordemExecucao"),java.util.Optional.ofNullable(jdbc.queryForObject("select coalesce(max(ordem_execucao),0)+1 from brasil_saas.bc_plm_efeito_mudanca where mudanca_id=? and empresa_id=?",Integer.class,id,u.getEmpresaId())).orElse(1));
  jdbc.update("insert into brasil_saas.bc_plm_efeito_mudanca(empresa_id,mudanca_id,entidade_tipo,entidade_id,acao,revisao_anterior,revisao_nova,efetiva_em,status,observacao,ordem_execucao,obrigatorio) values(?,?,?,?,?,?,?,?, 'PENDENTE',?,?,?)",
   u.getEmpresaId(),id,tipo,entidade,acao,b.get("revisaoAnterior"),b.get("revisaoNova"),b.get("efetivaEm"),ordem,b.get("observacao"),b.getOrDefault("obrigatorio",true));
  return Map.of("ok",true,"ordemExecucao",ordem);
 }

 @PostMapping("/plm/mudancas/{id}/aprovar")
 @PreAuthorize("hasAuthority('plm:escrita')")
 public Map<String,Object> aprovarMudanca(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  ensure("select count(*) from brasil_saas.bc_plm_mudanca where id=? and empresa_id=? and status='EM_APROVACAO'",id,u.getEmpresaId(),"Mudança não está aguardando aprovação");
  int etapa=integerValue(b.get("etapa"),1);
  String decisao=String.valueOf(b.getOrDefault("decisao","APROVADO")).toUpperCase(Locale.ROOT);
  if(!Set.of("APROVADO","REJEITADO").contains(decisao)) throw new IllegalArgumentException("Decisão inválida");
  jdbc.update("update brasil_saas.bc_plm_aprovacao set decisao=?,aprovador_id=?,observacao=?,decidido_em=now() where empresa_id=? and mudanca_id=? and etapa=?",
   decisao,u.getId(),b.get("observacao"),u.getEmpresaId(),id,etapa);
  if("REJEITADO".equals(decisao)) jdbc.update("update brasil_saas.bc_plm_mudanca set status='REJEITADA',updated_at=now() where id=? and empresa_id=?",id,u.getEmpresaId());
  else {
   int pendentes=java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_plm_aprovacao where empresa_id=? and mudanca_id=? and obrigatoria=true and decisao='PENDENTE'",Integer.class,u.getEmpresaId(),id)).orElse(0);
   if(pendentes==0) jdbc.update("update brasil_saas.bc_plm_mudanca set status='APROVADA',aprovador_id=?,aprovado_em=now(),updated_at=now() where id=? and empresa_id=?",u.getId(),id,u.getEmpresaId());
  }
  return workflow(u,id);
 }
 @PostMapping("/plm/mudancas/{id}/implementar")
 @PreAuthorize("hasAuthority('plm:escrita')")
 @Transactional
 public Map<String,Object> implementarMudanca(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  ensure("select count(*) from brasil_saas.bc_plm_mudanca where id=? and empresa_id=? and status='APROVADA'",id,u.getEmpresaId(),"Mudança não aprovada");
  List<Map<String,Object>> efeitos=jdbc.queryForList("select * from brasil_saas.bc_plm_efeito_mudanca where mudanca_id=? and empresa_id=? order by ordem_execucao,id",id,u.getEmpresaId());
  if(efeitos.isEmpty()) throw new IllegalStateException("Mudança sem efeitos de engenharia");
  for(Map<String,Object> e:efeitos){
   Long eid=((Number)e.get("id")).longValue();
   try {
    aplicarEfeito(u,e);
    jdbc.update("update brasil_saas.bc_plm_efeito_mudanca set status='APLICADA',efetiva_em=coalesce(efetiva_em,current_date),aplicado_em=now(),aplicado_por=?,erro_implementacao=null where id=? and empresa_id=?",u.getId(),eid,u.getEmpresaId());
   } catch(RuntimeException ex) {
    jdbc.update("update brasil_saas.bc_plm_efeito_mudanca set status='ERRO',erro_implementacao=? where id=? and empresa_id=?",ex.getMessage(),eid,u.getEmpresaId());
    throw ex;
   }
  }
  jdbc.update("update brasil_saas.bc_plm_mudanca set status='IMPLEMENTADA',implementado_em=now(),updated_at=now() where id=? and empresa_id=?",id,u.getEmpresaId());
  return workflow(u,id);
 }
 @GetMapping("/ehs/riscos/{id}/workflow")
 @PreAuthorize("hasAuthority('ehs:leitura')")
 public Map<String,Object> riscoWorkflow(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  ensure("select count(*) from brasil_saas.bc_ehs_risco where id=? and empresa_id=?",id,u.getEmpresaId(),"Risco EHS não encontrado");
  Map<String,Object> r=new LinkedHashMap<>();
  r.put("risco",jdbc.queryForMap("select * from brasil_saas.bc_ehs_risco where id=? and empresa_id=?",id,u.getEmpresaId()));
  r.put("acoes",jdbc.queryForList("select * from brasil_saas.bc_ehs_acao where empresa_id=? and (ocorrencia_id is null or ocorrencia_id is not null) order by prazo nulls last,id desc",u.getEmpresaId()));
  return r;
 }

 @PostMapping("/ehs/acoes")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> criarAcaoEhs(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
  Object descricao=b.get("descricao");
  if(descricao==null||descricao.toString().isBlank()) throw new IllegalArgumentException("descricao é obrigatória");
  jdbc.update("insert into brasil_saas.bc_ehs_acao(empresa_id,ocorrencia_id,inspecao_id,descricao,responsavel,prazo,status,evidencia) values(?,?,?,?,?,?,?,?)",
   u.getEmpresaId(),longValue(b.get("ocorrenciaId")),longValue(b.get("inspecaoId")),descricao.toString(),b.get("responsavel"),b.get("prazo"),b.getOrDefault("status","ABERTA"),b.get("evidencia"));
  return jdbc.queryForMap("select * from brasil_saas.bc_ehs_acao where empresa_id=? order by id desc limit 1",u.getEmpresaId());
 }

 @PostMapping("/ehs/acoes/{id}/concluir")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> concluirAcaoEhs(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  int n=jdbc.update("update brasil_saas.bc_ehs_acao set status='CONCLUIDA',concluida_em=now(),evidencia=coalesce(?,evidencia) where id=? and empresa_id=?",
   b.get("evidencia"),id,u.getEmpresaId());
  if(n==0) throw new NoSuchElementException("Ação EHS não encontrada");
  return jdbc.queryForMap("select * from brasil_saas.bc_ehs_acao where id=? and empresa_id=?",id,u.getEmpresaId());
 }

 @PostMapping("/ehs/inspecoes/{id}/reabrir")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> reabrirInspecao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  int n=jdbc.update("update brasil_saas.bc_ehs_inspecao set status='ABERTA' where id=? and empresa_id=?",id,u.getEmpresaId());
  if(n==0) throw new NoSuchElementException("Inspeção EHS não encontrada");
  return jdbc.queryForMap("select * from brasil_saas.bc_ehs_inspecao where id=? and empresa_id=?",id,u.getEmpresaId());
 }

 @PostMapping("/ehs/permissoes/{id}/cancelar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> cancelarPermissao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  int n=jdbc.update("update brasil_saas.bc_ehs_permissao_trabalho set status='CANCELADA' where id=? and empresa_id=? and status not in ('CONCLUIDA','CANCELADA')",id,u.getEmpresaId());
  if(n==0) throw new NoSuchElementException("Permissão não encontrada ou já encerrada");
  return jdbc.queryForMap("select * from brasil_saas.bc_ehs_permissao_trabalho where id=? and empresa_id=?",id,u.getEmpresaId());
 }

 @PostMapping("/ehs/riscos/{id}/reavaliar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> reavaliarRisco(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  double p=number(b.get("probabilidade")),i=number(b.get("impacto"));
  ensure("select count(*) from brasil_saas.bc_ehs_risco where id=? and empresa_id=?",id,u.getEmpresaId(),"Risco EHS não encontrado");
  jdbc.update("update brasil_saas.bc_ehs_risco set probabilidade=?,impacto=?,nivel=?,revisado_em=now(),responsavel=coalesce(?,responsavel) where id=? and empresa_id=?",p,i,p*i,b.get("responsavel"),id,u.getEmpresaId());
  return Map.of("ok",true,"nivel",p*i);
 }
 @PostMapping("/ehs/inspecoes/{id}/encerrar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> encerrarInspecao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
  ensure("select count(*) from brasil_saas.bc_ehs_inspecao where id=? and empresa_id=?",id,u.getEmpresaId(),"Inspeção não encontrada");
  jdbc.update("update brasil_saas.bc_ehs_inspecao set status='ENCERRADA',resultado=?,observacao=coalesce(?,observacao) where id=? and empresa_id=?",b.getOrDefault("resultado","CONFORME"),b.get("observacao"),id,u.getEmpresaId());
  return Map.of("ok",true);
 }
 @PostMapping("/ehs/permissoes/{id}/aprovar")
 @PreAuthorize("hasAuthority('ehs:escrita')")
 public Map<String,Object> aprovarPermissao(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  ensure("select count(*) from brasil_saas.bc_ehs_permissao_trabalho where id=? and empresa_id=?",id,u.getEmpresaId(),"Permissão não encontrada");
  jdbc.update("update brasil_saas.bc_ehs_permissao_trabalho set status='APROVADA',aprovada_por=?,aprovada_em=now() where id=? and empresa_id=?",u.getId(),id,u.getEmpresaId());
  return Map.of("ok",true);
 }
 
 private void validarEfeito(AuthenticatedUser u,String tipo,String acao,Long id){
  if(id==null) throw new IllegalArgumentException("entidadeId é obrigatório");
  String sql=switch(tipo){
   case "REVISAO_PRODUTO" -> "select count(*) from brasil_saas.bc_plm_produto_revisao where id=? and empresa_id=? and deleted_at is null";
   case "ESTRUTURA" -> "select count(*) from brasil_saas.bc_prod_estrutura where id=? and empresa_id=? and deleted_at is null";
   case "ROTEIRO" -> "select count(*) from brasil_saas.bc_prod_roteiro where id=? and empresa_id=? and deleted_at is null";
   case "DOCUMENTO" -> "select count(*) from brasil_saas.bc_plm_documento where id=? and empresa_id=? and deleted_at is null";
   default -> throw new IllegalArgumentException("Tipo de entidade PLM inválido");
  };
  ensure(sql,id,u.getEmpresaId(),"Entidade do efeito não encontrada");
  Set<String> permitidas=switch(tipo){
   case "REVISAO_PRODUTO" -> Set.of("VIGENCIAR");
   case "ESTRUTURA" -> Set.of("ATIVAR","INATIVAR");
   case "ROTEIRO" -> Set.of("VIGENCIAR","INATIVAR");
   case "DOCUMENTO" -> Set.of("APROVAR","INATIVAR");
   default -> Set.of();
  };
  if(!permitidas.contains(acao)) throw new IllegalArgumentException("Ação incompatível com o tipo de efeito");
 }

 private void aplicarEfeito(AuthenticatedUser u,Map<String,Object> e){
  String tipo=String.valueOf(e.get("entidade_tipo")), acao=String.valueOf(e.get("acao"));
  Long id=((Number)e.get("entidade_id")).longValue();
  if("REVISAO_PRODUTO".equals(tipo)&&"VIGENCIAR".equals(acao)){
   Long produto=jdbc.queryForObject("select produto_id from brasil_saas.bc_plm_produto_revisao where id=? and empresa_id=?",Long.class,id,u.getEmpresaId());
   jdbc.update("update brasil_saas.bc_plm_produto_revisao set status='SUPERADA',vigente_ate=current_date,updated_at=now() where empresa_id=? and produto_id=? and id<>? and status='VIGENTE'",u.getEmpresaId(),produto,id);
   jdbc.update("update brasil_saas.bc_plm_produto_revisao set status='VIGENTE',vigente_desde=coalesce(vigente_desde,current_date),vigente_ate=null where id=? and empresa_id=?",id,u.getEmpresaId());
  } else if("ESTRUTURA".equals(tipo)){
   jdbc.update("update brasil_saas.bc_prod_estrutura set ativo=? where id=? and empresa_id=?", "ATIVAR".equals(acao),id,u.getEmpresaId());
  } else if("ROTEIRO".equals(tipo)){
   if("VIGENCIAR".equals(acao)){
    Long produto=jdbc.queryForObject("select produto_id from brasil_saas.bc_prod_roteiro where id=? and empresa_id=?",Long.class,id,u.getEmpresaId());
    jdbc.update("update brasil_saas.bc_prod_roteiro set ativo=false,updated_at=now() where empresa_id=? and produto_id=? and id<>?",u.getEmpresaId(),produto,id);
    jdbc.update("update brasil_saas.bc_prod_roteiro set ativo=true,updated_at=now() where id=? and empresa_id=?",id,u.getEmpresaId());
   } else jdbc.update("update brasil_saas.bc_prod_roteiro set ativo=false,updated_at=now() where id=? and empresa_id=?",id,u.getEmpresaId());
  } else if("DOCUMENTO".equals(tipo)){
   jdbc.update("update brasil_saas.bc_plm_documento set status=?,aprovado_por=?,aprovado_em=case when ?='APROVAR' then now() else aprovado_em end,updated_at=now() where id=? and empresa_id=?",
    "APROVAR".equals(acao)?"APROVADO":"OBSOLETO",u.getId(),acao,id,u.getEmpresaId());
  }
 }
 private void ensure(String sql,Object a,Object b,String message){
  Integer n=jdbc.queryForObject(sql,Integer.class,a,b);
  if(n==null||n==0) throw new NoSuchElementException(message);
 }
 private double number(Object v){return v==null?0d:Double.parseDouble(v.toString());}
 private String table(String r){String t=TABLES.get(r);if(t==null)throw new IllegalArgumentException("Recurso inválido");return t;}
 private Map<String,Object> sanitize(Map<String,Object> s){
  Map<String,Object> d=new LinkedHashMap<>(); s.forEach((k,v)->{if(k!=null&&k.matches("[a-z][a-z0-9_]*")&&!Set.of("id","uuid","empresa_id","created_by","updated_by","deleted_at").contains(k))d.put(k,v);});return d;
 }

 private static Map<String,String> tabelas(String... kv){
  Map<String,String> m=new LinkedHashMap<>();
  for(int i=0;i<kv.length;i+=2) m.put(kv[i],kv[i+1]);
  return Collections.unmodifiableMap(m);
 }
 private static Long longValue(Object v){
  if(v==null||v.toString().isBlank()) return null;
  return v instanceof Number n?n.longValue():Long.valueOf(v.toString().trim());
 }
 private static Integer integerValue(Object v,Integer padrao){
  if(v==null||v.toString().isBlank()) return padrao;
  return v instanceof Number n?n.intValue():Integer.valueOf(v.toString().trim());
 }
}
