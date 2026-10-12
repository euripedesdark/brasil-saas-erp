package br.com.brasil_saas.supplychain.controller;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.brasil_saas.supplychain.service.AtpTemporalCalculator;
import java.time.LocalDate;
import java.util.*;

@RestController("supplyChainTmsAtpController") @RequestMapping("/api/supply-chain/enterprise") @RequiredArgsConstructor
public class SupplyChainEnterpriseController {
 private final JdbcTemplate jdbc;
 @GetMapping("/demanda") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> demanda(@AuthenticationPrincipal AuthenticatedUser u){return jdbc.queryForList("select * from brasil_saas.bc_sc_demanda where empresa_id=? order by periodo desc",u.getEmpresaId());}
 @PostMapping("/demanda") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> demanda(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){jdbc.update("insert into brasil_saas.bc_sc_demanda(empresa_id,produto_id,local_id,periodo,tipo,quantidade,confianca,origem) values(?,?,?,?,?,?,?,?)",u.getEmpresaId(),b.get("produtoId"),b.get("localId"),b.get("periodo"),b.getOrDefault("tipo","PREVISAO"),b.getOrDefault("quantidade",0),b.get("confianca"),b.get("origem"));return Map.of("ok",true);}
 @PostMapping("/atp/calcular") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> atp(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam Long produtoId,@RequestParam(required=false) Long localId,@RequestParam String data){
   Long e=u.getEmpresaId();
   LocalDate dia; try{ dia=LocalDate.parse(data); }catch(RuntimeException ex){ throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Data invalida, use AAAA-MM-DD"); }
   // localId = deposito. Sem localId, considera todos os depositos da empresa.
   String fl=localId==null?"":" and deposito_id=?";
   Object[] pe=localId==null?new Object[]{e,produtoId}:new Object[]{e,produtoId,localId};
   java.math.BigDecimal est=java.util.Objects.requireNonNullElse(jdbc.queryForObject("select coalesce(sum(quantidade),0) from brasil_saas.bc_est_saldo where empresa_id=? and produto_id=? and deleted_at is null"+fl,java.math.BigDecimal.class,pe),java.math.BigDecimal.ZERO);
   // Status reais de reserva: RESERVADA e SEPARACAO (nao existe ATIVA).
   java.math.BigDecimal res=java.util.Objects.requireNonNullElse(jdbc.queryForObject("select coalesce(sum(quantidade),0) from brasil_saas.bc_est_reserva where empresa_id=? and produto_id=? and status in ('RESERVADA','SEPARACAO') and deleted_at is null"+fl,java.math.BigDecimal.class,pe),java.math.BigDecimal.ZERO);
   // Entradas futuras: pedidos de compra em aberto com previsao de entrega (pedido nao tem deposito,
   // entao so entram quando o calculo e da empresa toda).
   List<AtpTemporalCalculator.Movimento> entradas=new ArrayList<>();
   if(localId==null){
    for(Map<String,Object> r:jdbc.queryForList("select p.data_previsao_entrega d, sum(i.quantidade-i.quantidade_recebida) q from brasil_saas.bc_com_pedido p join brasil_saas.bc_com_pedido_item i on i.pedido_id=p.id and i.deleted_at is null where p.empresa_id=? and i.produto_id=? and p.deleted_at is null and p.status in ('ABERTO','PARCIAL') and p.data_previsao_entrega is not null group by p.data_previsao_entrega having sum(i.quantidade-i.quantidade_recebida)>0",e,produtoId)){
     Object dval=r.get("d");
     java.time.LocalDate dataEnt;
     if(dval instanceof java.sql.Date sd) dataEnt=sd.toLocalDate();
     else if(dval instanceof java.time.LocalDate ld) dataEnt=ld;
     else dataEnt=java.time.LocalDate.parse(dval.toString());
     entradas.add(new AtpTemporalCalculator.Movimento(dataEnt,(java.math.BigDecimal)r.get("q")));
    }
   }
   java.math.BigDecimal disp=AtpTemporalCalculator.disponivelEm(dia,est,res,entradas,null);
   java.math.BigDecimal atp=AtpTemporalCalculator.prometivel(dia,est,res,entradas,null);
   // local_id 0 = sentinela "empresa toda" (UNIQUE nao aceita NULL de forma util).
   Long localPersist=localId==null?0L:localId;
   jdbc.update("""
insert into brasil_saas.bc_sc_atp(empresa_id,produto_id,local_id,data,estoque_disponivel,reservas,quantidade_atp,quantidade_ctp)
   values(?,?,?,?,?,?,?,?) on conflict(empresa_id,produto_id,local_id,data) do update set estoque_disponivel=excluded.estoque_disponivel,reservas=excluded.reservas,quantidade_atp=excluded.quantidade_atp,quantidade_ctp=excluded.quantidade_ctp,calculado_em=now()""",
   e,produtoId,localPersist,dia,est,res,atp,atp);
   return Map.of("ok",true,"produtoId",produtoId,"data",data,"localId",localPersist,"estoque",est,"reservas",res,"disponivelNaData",disp,"atp",atp,"ctp",atp);
 }
 @GetMapping("/atp") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> atps(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(required=false) Long produtoId){return produtoId==null?jdbc.queryForList("select * from brasil_saas.bc_sc_atp where empresa_id=? order by data desc",u.getEmpresaId()):jdbc.queryForList("select * from brasil_saas.bc_sc_atp where empresa_id=? and produto_id=? order by data desc",u.getEmpresaId(),produtoId);}
 @PostMapping("/planejamento") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> planejamento(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){jdbc.update("insert into brasil_saas.bc_sc_planejamento(empresa_id,codigo,periodo_inicio,periodo_fim,tipo,parametros) values(?,?,?,?,?,?::jsonb) on conflict(empresa_id,codigo) do update set periodo_inicio=excluded.periodo_inicio,periodo_fim=excluded.periodo_fim,tipo=excluded.tipo,parametros=excluded.parametros",u.getEmpresaId(),b.get("codigo"),b.get("periodoInicio"),b.get("periodoFim"),b.get("tipo"),b.getOrDefault("parametros","{}"));return Map.of("ok",true);}
 @GetMapping("/rotas") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> rotas(@AuthenticationPrincipal AuthenticatedUser u){return jdbc.queryForList("select * from brasil_saas.bc_sc_rota where empresa_id=? order by origem,destino",u.getEmpresaId());}
 @PostMapping("/rotas") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> rota(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){jdbc.update("insert into brasil_saas.bc_sc_rota(empresa_id,codigo,origem,destino,distancia_km,tempo_minutos,custo_base,custo_km,pedagio,restricoes) values(?,?,?,?,?,?,?,?,?,?::jsonb) on conflict(empresa_id,codigo) do update set origem=excluded.origem,destino=excluded.destino,distancia_km=excluded.distancia_km,tempo_minutos=excluded.tempo_minutos,custo_base=excluded.custo_base,custo_km=excluded.custo_km,pedagio=excluded.pedagio,restricoes=excluded.restricoes",u.getEmpresaId(),b.get("codigo"),b.get("origem"),b.get("destino"),b.get("distanciaKm"),b.get("tempoMinutos"),b.getOrDefault("custoBase",0),b.getOrDefault("custoKm",0),b.getOrDefault("pedagio",0),b.getOrDefault("restricoes","{}"));return Map.of("ok",true);}
 @PostMapping("/cargas") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> carga(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){jdbc.update("insert into brasil_saas.bc_sc_carga(empresa_id,codigo,transportadora_id,veiculo,origem,destino,peso,volume,valor_mercadoria,rota_id,frete_estimado) values(?,?,?,?,?,?,?,?,?,?,?)",u.getEmpresaId(),b.get("codigo"),b.get("transportadoraId"),b.get("veiculo"),b.get("origem"),b.get("destino"),b.getOrDefault("peso",0),b.getOrDefault("volume",0),b.getOrDefault("valorMercadoria",0),b.get("rotaId"),b.getOrDefault("freteEstimado",0));return Map.of("ok",true);}
 @PostMapping("/cargas/{id}/tracking") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> tracking(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object>b){jdbc.update("insert into brasil_saas.bc_sc_tracking(empresa_id,carga_id,evento,localizacao,latitude,longitude,ocorrencia,comprovante) values(?,?,?,?,?,?,?,?)",u.getEmpresaId(),id,b.get("evento"),b.get("localizacao"),b.get("latitude"),b.get("longitude"),b.get("ocorrencia"),b.get("comprovante"));return Map.of("ok",true);}
 @GetMapping("/cargas/{id}/tracking") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> tracking(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){return jdbc.queryForList("select * from brasil_saas.bc_sc_tracking where empresa_id=? and carga_id=? order by data_evento",u.getEmpresaId(),id);}
 @PostMapping("/fretes/{cargaId}/conferir") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> frete(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long cargaId,@RequestBody Map<String,Object>b){jdbc.update("""
insert into brasil_saas.bc_sc_frete(empresa_id,carga_id,transportadora_id,documento,frete,pedagio,adicionais,total,status,conferido_em) values(?,?,?,?,?,?,?,?, 'CONFERIDO',now()) on conflict(empresa_id,carga_id) do update set frete=excluded.frete,pedagio=excluded.pedagio,adicionais=excluded.adicionais,total=excluded.total,status='CONFERIDO',conferido_em=now()""",u.getEmpresaId(),cargaId,b.get("transportadoraId"),b.get("documento"),b.getOrDefault("frete",0),b.getOrDefault("pedagio",0),b.getOrDefault("adicionais",0),b.getOrDefault("total",0));return Map.of("ok",true);}
}