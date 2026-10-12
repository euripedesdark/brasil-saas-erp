package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
public class RiskTransportEnterpriseController {
    private final JdbcTemplate jdbc;

    @GetMapping("/grc/riscos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> riscos(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select * from brasil_saas.bc_grc_risco where empresa_id=? order by status, nivel desc, id desc", u.getEmpresaId());
    }

    @PostMapping("/grc/riscos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> risco(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        double p = number(b.get("probabilidade")), i = number(b.get("impacto"));
        jdbc.update("insert into brasil_saas.bc_grc_risco(empresa_id,codigo,descricao,categoria,probabilidade,impacto,nivel,status,responsavel,prazo) values(?,?,?,?,?,?,?,?,?,?)",
                u.getEmpresaId(), required(b,"codigo"), required(b,"descricao"), b.get("categoria"), p, i, p*i,
                b.getOrDefault("status","ABERTO"), b.get("responsavel"), b.get("prazo"));
        return Map.of("ok", true, "nivel", p*i);
    }

    @PutMapping("/grc/riscos/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> atualizarRisco(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        ensure("select count(*) from brasil_saas.bc_grc_risco where id=? and empresa_id=?",id,u.getEmpresaId(),"Risco inválido");
        Object p=b.get("probabilidade"),i=b.get("impacto");
        jdbc.update("update brasil_saas.bc_grc_risco set descricao=coalesce(?,descricao),categoria=coalesce(?,categoria),probabilidade=coalesce(?,probabilidade),impacto=coalesce(?,impacto),nivel=case when ? is null and ? is null then nivel else coalesce(?,probabilidade)*coalesce(?,impacto) end,status=coalesce(?,status),responsavel=coalesce(?,responsavel),prazo=coalesce(?,prazo),updated_at=now() where id=? and empresa_id=?",
            b.get("descricao"),b.get("categoria"),p,i,p,i,p,i,b.get("status"),b.get("responsavel"),b.get("prazo"),id,u.getEmpresaId());
        return Map.of("ok",true);
    }

    @PutMapping("/grc/controles/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> atualizarControle(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        ensure("select count(*) from brasil_saas.bc_grc_controle where id=? and empresa_id=?",id,u.getEmpresaId(),"Controle inválido");
        jdbc.update("update brasil_saas.bc_grc_controle set descricao=coalesce(?,descricao),tipo=coalesce(?,tipo),frequencia=coalesce(?,frequencia),responsavel=coalesce(?,responsavel),status=coalesce(?,status) where id=? and empresa_id=?",b.get("descricao"),b.get("tipo"),b.get("frequencia"),b.get("responsavel"),b.get("status"),id,u.getEmpresaId());
        return Map.of("ok",true);
    }

    @GetMapping("/grc/vinculos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> vinculos(@AuthenticationPrincipal AuthenticatedUser u){
        return jdbc.queryForList("select rc.*,r.codigo risco_codigo,c.codigo controle_codigo from brasil_saas.bc_grc_risco_controle rc join brasil_saas.bc_grc_risco r on r.id=rc.risco_id and r.empresa_id=rc.empresa_id join brasil_saas.bc_grc_controle c on c.id=rc.controle_id and c.empresa_id=rc.empresa_id where rc.empresa_id=? order by r.codigo,c.codigo",u.getEmpresaId());
    }

    @PutMapping("/tms/ordens/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> atualizarOrdem(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        ensure("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?",id,u.getEmpresaId(),"Ordem inválida");
        jdbc.update("update brasil_saas.bc_tms_ordem set origem=coalesce(?,origem),destino=coalesce(?,destino),transportadora_id=coalesce(?,transportadora_id),rota_id=coalesce(?,rota_id),modalidade=coalesce(?,modalidade),veiculo=coalesce(?,veiculo),motorista=coalesce(?,motorista),data_prevista_saida=coalesce(?,data_prevista_saida),data_prevista_entrega=coalesce(?,data_prevista_entrega),peso=coalesce(?,peso),volume=coalesce(?,volume),frete_previsto=coalesce(?,frete_previsto) where id=? and empresa_id=?",
            b.get("origem"),b.get("destino"),longValue(b.get("transportadoraId")),longValue(b.get("rotaId")),b.get("modalidade"),b.get("veiculo"),b.get("motorista"),b.get("dataPrevistaSaida"),b.get("dataPrevistaEntrega"),b.get("peso"),b.get("volume"),b.get("fretePrevisto"),id,u.getEmpresaId());
        return Map.of("ok",true);
    }

    @GetMapping("/grc/controles")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> controles(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select * from brasil_saas.bc_grc_controle where empresa_id=? order by codigo", u.getEmpresaId());
    }

    @PostMapping("/grc/controles")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> controle(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        jdbc.update("insert into brasil_saas.bc_grc_controle(empresa_id,codigo,descricao,tipo,frequencia,responsavel,status) values(?,?,?,?,?,?,?)",
                u.getEmpresaId(), required(b,"codigo"), required(b,"descricao"), b.getOrDefault("tipo","PREVENTIVO"),
                b.get("frequencia"), b.get("responsavel"), b.getOrDefault("status","ATIVO"));
        return Map.of("ok", true);
    }

    @PostMapping("/grc/vinculos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> vinculo(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId")), controle=longValue(b.get("controleId"));
        if(risco==null||controle==null||java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_grc_risco r join brasil_saas.bc_grc_controle c on c.empresa_id=r.empresa_id where r.id=? and c.id=? and r.empresa_id=?", Integer.class,risco,controle,u.getEmpresaId())).orElse(0) == 0)
            throw new IllegalArgumentException("Risco/controle inválido para a empresa");
        jdbc.update("insert into brasil_saas.bc_grc_risco_controle(empresa_id,risco_id,controle_id) values(?,?,?) on conflict do nothing",u.getEmpresaId(),risco,controle);
        return Map.of("ok",true);
    }

    @GetMapping("/grc/evidencias")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> evidencias(@AuthenticationPrincipal AuthenticatedUser u){
        return jdbc.queryForList("select * from brasil_saas.bc_grc_evidencia where empresa_id=? order by id desc",u.getEmpresaId());
    }

    @PostMapping("/grc/evidencias")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> evidencia(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        jdbc.update("insert into brasil_saas.bc_grc_evidencia(empresa_id,entidade_tipo,entidade_id,nome,localizacao,validade,hash_documento) values(?,?,?,?,?,?,?)",
            u.getEmpresaId(),required(b,"entidadeTipo"),longValue(b.get("entidadeId")),required(b,"nome"),b.get("localizacao"),b.get("validade"),b.get("hashDocumento"));
        return Map.of("ok",true);
    }

    @GetMapping("/grc/testes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> testes(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select t.*, c.codigo controle_codigo from brasil_saas.bc_grc_teste_controle t join brasil_saas.bc_grc_controle c on c.id=t.controle_id and c.empresa_id=t.empresa_id where t.empresa_id=? order by t.periodo desc,t.id desc", u.getEmpresaId());
    }

    @PostMapping("/grc/testes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> teste(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        Long controle = longValue(b.get("controleId"));
        if (controle == null || java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_grc_controle where id=? and empresa_id=?", Integer.class, controle,u.getEmpresaId())).orElse(0) == 0)
            throw new IllegalArgumentException("Controle inválido para a empresa");
        jdbc.update("insert into brasil_saas.bc_grc_teste_controle(empresa_id,controle_id,periodo,resultado,observacao,testado_por,testado_em) values(?,?,?,?,?,?,now()) on conflict (empresa_id,controle_id,periodo) do update set resultado=excluded.resultado,observacao=excluded.observacao,testado_por=excluded.testado_por,testado_em=now()",
                u.getEmpresaId(), controle, required(b,"periodo"), b.getOrDefault("resultado","PENDENTE"), b.get("observacao"), b.get("testadoPor"));
        return Map.of("ok", true);
    }

    @GetMapping("/grc/planos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> planos(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select p.*,r.codigo risco_codigo from brasil_saas.bc_grc_plano_acao p join brasil_saas.bc_grc_risco r on r.id=p.risco_id and r.empresa_id=p.empresa_id where p.empresa_id=? order by p.prazo nulls last,p.id desc",u.getEmpresaId()); }

    @PostMapping("/grc/planos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> plano(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId")); ensure("select count(*) from brasil_saas.bc_grc_risco where id=? and empresa_id=?",risco,u.getEmpresaId(),"Risco inválido");
        jdbc.update("insert into brasil_saas.bc_grc_plano_acao(empresa_id,risco_id,descricao,responsavel,prazo,status,percentual_conclusao,evidencia_id) values(?,?,?,?,?,?,?,?)",u.getEmpresaId(),risco,required(b,"descricao"),b.get("responsavel"),b.get("prazo"),b.getOrDefault("status","ABERTO"),number(b.get("percentualConclusao")),longValue(b.get("evidenciaId")));
        return Map.of("ok",true);
    }

    @PutMapping("/grc/planos/{id}")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> atualizarPlano(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){
        ensure("select count(*) from brasil_saas.bc_grc_plano_acao where id=? and empresa_id=?",id,u.getEmpresaId(),"Plano inválido");
        jdbc.update("update brasil_saas.bc_grc_plano_acao set descricao=coalesce(?,descricao),responsavel=coalesce(?,responsavel),prazo=coalesce(?,prazo),status=coalesce(?,status),percentual_conclusao=coalesce(?,percentual_conclusao),evidencia_id=coalesce(?,evidencia_id),updated_at=now() where id=? and empresa_id=?",b.get("descricao"),b.get("responsavel"),b.get("prazo"),b.get("status"),b.get("percentualConclusao"),longValue(b.get("evidenciaId")),id,u.getEmpresaId());
        return Map.of("ok",true);
    }

    @GetMapping("/grc/avaliacoes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> avaliacoes(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select a.*,r.codigo risco_codigo from brasil_saas.bc_grc_avaliacao a join brasil_saas.bc_grc_risco r on r.id=a.risco_id and r.empresa_id=a.empresa_id where a.empresa_id=? order by a.periodo desc,a.id desc",u.getEmpresaId()); }

    @PostMapping("/grc/avaliacoes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> avaliacao(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){
        Long risco=longValue(b.get("riscoId")); double p=number(b.get("probabilidade")),i=number(b.get("impacto")); ensure("select count(*) from brasil_saas.bc_grc_risco where id=? and empresa_id=?",risco,u.getEmpresaId(),"Risco inválido");
        jdbc.update("insert into brasil_saas.bc_grc_avaliacao(empresa_id,risco_id,periodo,probabilidade,impacto,nivel,tendencia,avaliador,observacao) values(?,?,?,?,?,?,?,?,?) on conflict (empresa_id,risco_id,periodo) do update set probabilidade=excluded.probabilidade,impacto=excluded.impacto,nivel=excluded.nivel,tendencia=excluded.tendencia,avaliador=excluded.avaliador,observacao=excluded.observacao,avaliado_em=now()",u.getEmpresaId(),risco,required(b,"periodo"),p,i,p*i,b.get("tendencia"),b.get("avaliador"),b.get("observacao"));
        return Map.of("ok",true,"nivel",p*i);
    }

    @GetMapping("/tms/ordens")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> ordens(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select * from brasil_saas.bc_tms_ordem where empresa_id=? order by id desc", u.getEmpresaId());
    }

    @PostMapping("/tms/ordens")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> ordem(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        jdbc.update("insert into brasil_saas.bc_tms_ordem(empresa_id,numero,origem,destino,transportadora_id,rota_id,modalidade,status,data_prevista_saida,data_prevista_entrega,peso,volume,frete_previsto,frete_real,veiculo,motorista) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                u.getEmpresaId(), required(b,"numero"), b.get("origem"), b.get("destino"), longValue(b.get("transportadoraId")), longValue(b.get("rotaId")),
                b.getOrDefault("modalidade","RODOVIARIO"), b.getOrDefault("status","PLANEJADA"),
                b.get("dataPrevistaSaida"), b.get("dataPrevistaEntrega"), number(b.get("peso")), number(b.get("volume")),
                number(b.get("fretePrevisto")), number(b.get("freteReal")), b.get("veiculo"), b.get("motorista"));
        return Map.of("ok", true);
    }

    @GetMapping("/tms/rotas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> rotas(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select * from brasil_saas.bc_tms_rota where empresa_id=? order by codigo",u.getEmpresaId()); }

    @PostMapping("/tms/rotas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> rota(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){ jdbc.update("insert into brasil_saas.bc_tms_rota(empresa_id,codigo,descricao,origem,destino,distancia_km,tempo_estimado_min,pedagio_estimado,ativo) values(?,?,?,?,?,?,?,?,?)",u.getEmpresaId(),required(b,"codigo"),b.get("descricao"),required(b,"origem"),required(b,"destino"),number(b.get("distanciaKm")),intValue(b.get("tempoEstimadoMin")),number(b.get("pedagioEstimado")),b.getOrDefault("ativo",true)); return Map.of("ok",true); }

    @PostMapping("/tms/ordens/{id}/status")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> statusOrdem(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object> b){ ensure("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?",id,u.getEmpresaId(),"Ordem inválida"); String s=required(b,"status"); jdbc.update("update brasil_saas.bc_tms_ordem set status=?,data_saida=case when ?='EM_TRANSITO' then coalesce(data_saida,now()) else data_saida end,data_entrega=case when ?='ENTREGUE' then coalesce(data_entrega,now()) else data_entrega end where id=? and empresa_id=?",s,s,s,id,u.getEmpresaId()); jdbc.update("insert into brasil_saas.bc_tms_evento(empresa_id,ordem_id,tipo,descricao) values(?,?,?,?)",u.getEmpresaId(),id,"STATUS",s); return Map.of("ok",true); }

    @GetMapping("/tms/paradas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> paradas(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select p.*,o.numero from brasil_saas.bc_tms_parada p join brasil_saas.bc_tms_ordem o on o.id=p.ordem_id and o.empresa_id=p.empresa_id where p.empresa_id=? order by p.ordem_id,p.sequencia",u.getEmpresaId()); }

    @PostMapping("/tms/paradas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> parada(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){ Long id=longValue(b.get("ordemId")); ensure("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?",id,u.getEmpresaId(),"Ordem inválida"); jdbc.update("insert into brasil_saas.bc_tms_parada(empresa_id,ordem_id,sequencia,tipo,localizacao,prevista_em,status) values(?,?,?,?,?,?,?)",u.getEmpresaId(),id,intValue(b.get("sequencia")),required(b,"tipo"),required(b,"localizacao"),b.get("previstaEm"),b.getOrDefault("status","PENDENTE")); return Map.of("ok",true); }

    @PostMapping("/tms/paradas/{id}/realizar")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> realizarParada(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){ ensure("select count(*) from brasil_saas.bc_tms_parada where id=? and empresa_id=?",id,u.getEmpresaId(),"Parada inválida"); jdbc.update("update brasil_saas.bc_tms_parada set status='REALIZADA',realizada_em=now() where id=? and empresa_id=?",id,u.getEmpresaId()); return Map.of("ok",true); }

    @GetMapping("/tms/entregas")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> entregas(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select d.*,o.numero from brasil_saas.bc_tms_documento_entrega d join brasil_saas.bc_tms_ordem o on o.id=d.ordem_id and o.empresa_id=d.empresa_id where d.empresa_id=? order by d.id desc",u.getEmpresaId()); }

    @PostMapping("/tms/entregas")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> entrega(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){ Long id=longValue(b.get("ordemId")); ensure("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?",id,u.getEmpresaId(),"Ordem inválida"); String s=String.valueOf(b.getOrDefault("status","RECEBIDO")); jdbc.update("insert into brasil_saas.bc_tms_documento_entrega(empresa_id,ordem_id,tipo,numero,recebedor,recebido_em,assinatura_localizacao,observacao,documento_localizacao,status) values(?,?,?,?,?,?,?,?,?,?)",u.getEmpresaId(),id,b.getOrDefault("tipo","COMPROVANTE_ENTREGA"),b.get("numero"),b.get("recebedor"),b.get("recebidoEm"),b.get("assinaturaLocalizacao"),b.get("observacao"),b.get("documentoLocalizacao"),s); if("RECEBIDO".equals(s)) jdbc.update("update brasil_saas.bc_tms_ordem set status='ENTREGUE' where id=? and empresa_id=?",id,u.getEmpresaId()); return Map.of("ok",true); }

    @GetMapping("/tms/fechamentos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> fechamentos(@AuthenticationPrincipal AuthenticatedUser u){ return jdbc.queryForList("select f.*,o.numero from brasil_saas.bc_tms_fechamento f join brasil_saas.bc_tms_ordem o on o.id=f.ordem_id and o.empresa_id=f.empresa_id where f.empresa_id=? order by f.id desc",u.getEmpresaId()); }

    @PostMapping("/tms/fechamentos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> fechamento(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b){ Long id=longValue(b.get("ordemId")); ensure("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?",id,u.getEmpresaId(),"Ordem inválida"); double c=number(b.get("freteContratado")),a=number(b.get("adicionais")),d=number(b.get("descontos")),total=c+a-d; jdbc.update("insert into brasil_saas.bc_tms_fechamento(empresa_id,ordem_id,frete_contratado,adicionais,descontos,frete_aprovado,documento,status,aprovado_por,aprovado_em) values(?,?,?,?,?,?,?,?,?,?) on conflict (empresa_id,ordem_id) do update set frete_contratado=excluded.frete_contratado,adicionais=excluded.adicionais,descontos=excluded.descontos,frete_aprovado=excluded.frete_aprovado,documento=excluded.documento,status=excluded.status,aprovado_por=excluded.aprovado_por,aprovado_em=excluded.aprovado_em",u.getEmpresaId(),id,c,a,d,total,b.get("documento"),b.getOrDefault("status","APROVADO"),b.get("aprovadoPor"),"APROVADO".equals(b.getOrDefault("status","APROVADO"))?new java.sql.Timestamp(System.currentTimeMillis()):null); jdbc.update("update brasil_saas.bc_tms_ordem set frete_real=? where id=? and empresa_id=?",total,id,u.getEmpresaId()); return Map.of("ok",true,"freteAprovado",total); }

    @GetMapping("/tms/eventos")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> eventos(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select e.*, o.numero from brasil_saas.bc_tms_evento e join brasil_saas.bc_tms_ordem o on o.id=e.ordem_id and o.empresa_id=e.empresa_id where e.empresa_id=? order by e.data_evento desc", u.getEmpresaId());
    }

    @PostMapping("/tms/eventos")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> evento(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String,Object> b) {
        Long ordem = longValue(b.get("ordemId"));
        if (ordem == null || java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?", Integer.class, ordem,u.getEmpresaId())).orElse(0) == 0)
            throw new IllegalArgumentException("Ordem de transporte inválida para a empresa");
        jdbc.update("insert into brasil_saas.bc_tms_evento(empresa_id,ordem_id,tipo,data_evento,localizacao,descricao) values(?,?,?,?,?,?)",
                u.getEmpresaId(),ordem,required(b,"tipo"),b.get("dataEvento"),b.get("localizacao"),b.get("descricao"));
        if (b.get("status") != null) jdbc.update("update brasil_saas.bc_tms_ordem set status=? where id=? and empresa_id=?", b.get("status"),ordem,u.getEmpresaId());
        return Map.of("ok", true);
    }

    @GetMapping("/tms/tracking")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> tracking(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select t.*,o.numero,o.origem,o.destino,o.status ordem_status from brasil_saas.bc_tms_tracking t join brasil_saas.bc_tms_ordem o on o.id=t.ordem_id and o.empresa_id=t.empresa_id where t.empresa_id=? order by t.ultima_atualizacao desc nulls last",u.getEmpresaId());
    }

    @PostMapping("/tms/tracking")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> trackingSave(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b) {
        Long ordem=longValue(b.get("ordemId"));
        if(ordem==null || java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?", Integer.class,ordem,u.getEmpresaId())).orElse(0) == 0)
            throw new IllegalArgumentException("Ordem de transporte inválida para a empresa");
        jdbc.update("insert into brasil_saas.bc_tms_tracking(empresa_id,ordem_id,codigo,transportadora,ultimo_status,ultima_atualizacao) values(?,?,?,?,?,now()) on conflict (empresa_id,ordem_id) do update set codigo=excluded.codigo,transportadora=excluded.transportadora,ultimo_status=excluded.ultimo_status,ultima_atualizacao=now()",
                u.getEmpresaId(),ordem,required(b,"codigo"),b.get("transportadora"),b.get("ultimoStatus"));
        return Map.of("ok",true);
    }

    @GetMapping("/tms/fretes")
    @PreAuthorize("hasAuthority('enterprise:leitura')")
    public List<Map<String,Object>> fretes(@AuthenticationPrincipal AuthenticatedUser u) {
        return jdbc.queryForList("select f.*,o.numero from brasil_saas.bc_tms_frete f join brasil_saas.bc_tms_ordem o on o.id=f.ordem_id and o.empresa_id=f.empresa_id where f.empresa_id=? order by f.id desc",u.getEmpresaId());
    }

    @PostMapping("/tms/fretes")
    @PreAuthorize("hasAuthority('enterprise:escrita')")
    public Map<String,Object> frete(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object> b) {
        Long ordem=longValue(b.get("ordemId"));
        if(ordem==null || java.util.Optional.ofNullable(jdbc.queryForObject("select count(*) from brasil_saas.bc_tms_ordem where id=? and empresa_id=?", Integer.class,ordem,u.getEmpresaId())).orElse(0) == 0)
            throw new IllegalArgumentException("Ordem de transporte inválida para a empresa");
        jdbc.update("insert into brasil_saas.bc_tms_frete(empresa_id,ordem_id,componente,valor,documento,status) values(?,?,?,?,?,?)",
                u.getEmpresaId(),ordem,required(b,"componente"),number(b.get("valor")),b.get("documento"),b.getOrDefault("status","PENDENTE"));
        return Map.of("ok",true);
    }

    private void ensure(String sql,Object id,Object empresa,String msg){if(id==null||jdbc.queryForObject(sql,Integer.class,id,empresa)==0)throw new IllegalArgumentException(msg);}
    private static int intValue(Object v){return v==null?0:Integer.parseInt(v.toString());}
    private static String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new IllegalArgumentException(k+" é obrigatório");return v.toString();}
    private static double number(Object v){return v==null?0:Double.parseDouble(v.toString());}
    private static Long longValue(Object v){return v==null?null:Long.valueOf(v.toString());}
}
