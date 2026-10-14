package br.com.brasil_saas.supplychain.controller;
import br.com.brasil_saas.compras.repository.ItemPedidoCompraRepository;
import br.com.brasil_saas.estoque.repository.ReservaEstoqueRepository;
import br.com.brasil_saas.estoque.repository.SaldoEstoqueRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.web.MapValues;
import br.com.brasil_saas.supplychain.model.ScAtp;
import br.com.brasil_saas.supplychain.model.ScCarga;
import br.com.brasil_saas.supplychain.model.ScDemanda;
import br.com.brasil_saas.supplychain.model.ScFrete;
import br.com.brasil_saas.supplychain.model.ScPlanejamento;
import br.com.brasil_saas.supplychain.model.ScRota;
import br.com.brasil_saas.supplychain.model.ScTracking;
import br.com.brasil_saas.supplychain.repository.ScAtpRepository;
import br.com.brasil_saas.supplychain.repository.ScCargaRepository;
import br.com.brasil_saas.supplychain.repository.ScDemandaRepository;
import br.com.brasil_saas.supplychain.repository.ScFreteRepository;
import br.com.brasil_saas.supplychain.repository.ScPlanejamentoRepository;
import br.com.brasil_saas.supplychain.repository.ScRotaRepository;
import br.com.brasil_saas.supplychain.repository.ScTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import br.com.brasil_saas.supplychain.service.AtpTemporalCalculator;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController("supplyChainTmsAtpController") @RequestMapping("/api/supply-chain/enterprise") @RequiredArgsConstructor
public class SupplyChainEnterpriseController {
 private final ScDemandaRepository demandaRepository;
 private final ScAtpRepository atpRepository;
 private final ScPlanejamentoRepository planejamentoRepository;
 private final ScRotaRepository rotaRepository;
 private final ScCargaRepository cargaRepository;
 private final ScTrackingRepository trackingRepository;
 private final ScFreteRepository freteRepository;
 private final SaldoEstoqueRepository saldoRepository;
 private final ReservaEstoqueRepository reservaRepository;
 private final ItemPedidoCompraRepository itemPedidoCompraRepository;

 @GetMapping("/demanda") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> demanda(@AuthenticationPrincipal AuthenticatedUser u){
   return demandaRepository.findByEmpresaIdOrderByPeriodoDesc(u.getEmpresaId()).stream().map(d -> {
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("id", d.getId()); m.put("empresa_id", d.getEmpresaId()); m.put("produto_id", d.getProdutoId());
     m.put("local_id", d.getLocalId()); m.put("periodo", d.getPeriodo()); m.put("tipo", d.getTipo());
     m.put("quantidade", d.getQuantidade()); m.put("confianca", d.getConfianca()); m.put("origem", d.getOrigem());
     m.put("status", d.getStatus()); m.put("created_at", d.getCreatedAt());
     return m;
   }).toList();
 }
 @PostMapping("/demanda") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> demanda(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   ScDemanda d = new ScDemanda();
   d.setEmpresaId(u.getEmpresaId());
   if (MapValues.lng(b.get("produtoId")) == null) throw new IllegalArgumentException("produtoId é obrigatório");
   if (MapValues.dt(b.get("periodo")) == null) throw new IllegalArgumentException("periodo é obrigatório");
   d.setProdutoId(MapValues.lng(b.get("produtoId")));
   d.setLocalId(MapValues.lng(b.get("localId")));
   d.setPeriodo(MapValues.dt(b.get("periodo")));
   d.setTipo(b.get("tipo") == null ? "PREVISAO" : String.valueOf(b.getOrDefault("tipo","PREVISAO")));
   d.setQuantidade(MapValues.bd(b.getOrDefault("quantidade",0)) == null ? java.math.BigDecimal.ZERO : MapValues.bd(b.getOrDefault("quantidade",0)));
   d.setConfianca(MapValues.bd(b.get("confianca")));
   d.setOrigem(b.get("origem") == null ? null : String.valueOf(b.get("origem")));
   demandaRepository.save(d);
   return Map.of("ok",true);
 }
 @PostMapping("/atp/calcular") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> atp(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam Long produtoId,@RequestParam(required=false) Long localId,@RequestParam String data){
   Long e=u.getEmpresaId();
   LocalDate dia; try{ dia=LocalDate.parse(data); }catch(RuntimeException ex){ throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Data invalida, use AAAA-MM-DD"); }
   // localId = deposito. Sem localId, considera todos os depositos da empresa.
   java.math.BigDecimal est = localId == null
     ? saldoRepository.somaPorProduto(e, produtoId)
     : saldoRepository.somaPorProdutoEDeposito(e, produtoId, localId);
   est = est == null ? java.math.BigDecimal.ZERO : est;
   // Status reais de reserva: RESERVADA e SEPARACAO (nao existe ATIVA).
   java.math.BigDecimal res = localId == null
     ? reservaRepository.somaReservada(e, produtoId)
     : reservaRepository.somaReservadaPorDeposito(e, produtoId, localId);
   res = res == null ? java.math.BigDecimal.ZERO : res;
   // Entradas futuras: pedidos de compra em aberto com previsao de entrega (pedido nao tem deposito,
   // entao so entram quando o calculo e da empresa toda).
   List<AtpTemporalCalculator.Movimento> entradas=new ArrayList<>();
   if(localId==null){
    for(ItemPedidoCompraRepository.EntradaPrevista r : itemPedidoCompraRepository.entradasFuturas(e,produtoId)){
     entradas.add(new AtpTemporalCalculator.Movimento(r.getDataEntrega(), r.getSaldo()));
    }
   }
   java.math.BigDecimal disp=AtpTemporalCalculator.disponivelEm(dia,est,res,entradas,null);
   java.math.BigDecimal atp=AtpTemporalCalculator.prometivel(dia,est,res,entradas,null);
   // local_id 0 = sentinela "empresa toda" (UNIQUE nao aceita NULL de forma util).
   Long localPersist=localId==null?0L:localId;
   ScAtp a = atpRepository.findByEmpresaIdAndProdutoIdAndLocalIdAndData(e,produtoId,localPersist,dia)
     .orElseGet(ScAtp::new);
   a.setEmpresaId(e); a.setProdutoId(produtoId); a.setLocalId(localPersist); a.setData(dia);
   a.setEstoqueDisponivel(est); a.setReservas(res); a.setQuantidadeAtp(atp); a.setQuantidadeCtp(atp);
   a.setCalculadoEm(LocalDateTime.now());
   atpRepository.save(a);
   return Map.of("ok",true,"produtoId",produtoId,"data",data,"localId",localPersist,"estoque",est,"reservas",res,"disponivelNaData",disp,"atp",atp,"ctp",atp);
 }
 @GetMapping("/atp") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> atps(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam(required=false) Long produtoId){
   List<ScAtp> linhas = produtoId==null
     ? atpRepository.findByEmpresaIdOrderByDataDesc(u.getEmpresaId())
     : atpRepository.findByEmpresaIdAndProdutoIdOrderByDataDesc(u.getEmpresaId(),produtoId);
   return linhas.stream().map(a -> {
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("id", a.getId()); m.put("empresa_id", a.getEmpresaId()); m.put("produto_id", a.getProdutoId());
     m.put("local_id", a.getLocalId()); m.put("data", a.getData());
     m.put("estoque_disponivel", a.getEstoqueDisponivel());
     m.put("entradas_confirmadas", a.getEntradasConfirmadas());
     m.put("reservas", a.getReservas()); m.put("demanda_aberta", a.getDemandaAberta());
     m.put("quantidade_atp", a.getQuantidadeAtp()); m.put("quantidade_ctp", a.getQuantidadeCtp());
     m.put("calculado_em", a.getCalculadoEm());
     return m;
   }).toList();
 }
 @PostMapping("/planejamento") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> planejamento(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   Long e = u.getEmpresaId();
   String codigo = MapValues.required(b, "codigo");
   ScPlanejamento p = planejamentoRepository.findByEmpresaIdAndCodigo(e, codigo).orElseGet(ScPlanejamento::new);
   p.setEmpresaId(e); p.setCodigo(codigo);
   p.setPeriodoInicio(MapValues.dt(b.get("periodoInicio")));
   p.setPeriodoFim(MapValues.dt(b.get("periodoFim")));
   p.setTipo(b.get("tipo") == null ? null : String.valueOf(b.get("tipo")));
   Object par = b.getOrDefault("parametros","{}");
   p.setParametros(par == null ? "{}" : String.valueOf(par));
   planejamentoRepository.save(p);
   return Map.of("ok",true);
 }
 @GetMapping("/rotas") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> rotas(@AuthenticationPrincipal AuthenticatedUser u){
   return rotaRepository.findByEmpresaIdOrderByOrigemAscDestinoAsc(u.getEmpresaId()).stream().map(r -> {
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("id", r.getId()); m.put("empresa_id", r.getEmpresaId()); m.put("codigo", r.getCodigo());
     m.put("origem", r.getOrigem()); m.put("destino", r.getDestino());
     m.put("distancia_km", r.getDistanciaKm()); m.put("tempo_minutos", r.getTempoMinutos());
     m.put("custo_base", r.getCustoBase()); m.put("custo_km", r.getCustoKm());
     m.put("pedagio", r.getPedagio()); m.put("restricoes", r.getRestricoes()); m.put("ativo", r.getAtivo());
     return m;
   }).toList();
 }
 @PostMapping("/rotas") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> rota(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   ScRota r = new ScRota();
   r.setEmpresaId(u.getEmpresaId());
   r.setCodigo(MapValues.required(b, "codigo"));
   r.setOrigem(MapValues.str(b.get("origem"))); r.setDestino(MapValues.str(b.get("destino")));
   r.setDistanciaKm(MapValues.bd(b.get("distanciaKm"))); r.setTempoMinutos(MapValues.integer(b.get("tempoMinutos")));
   r.setCustoBase(MapValues.bd(b.getOrDefault("custoBase",0)));
   r.setCustoKm(MapValues.bd(b.getOrDefault("custoKm",0)));
   r.setPedagio(MapValues.bd(b.getOrDefault("pedagio",0)));
   Object rest = b.getOrDefault("restricoes","{}");
   r.setRestricoes(rest == null ? "{}" : String.valueOf(rest));
   rotaRepository.save(r);
   return Map.of("ok",true);
 }
 @PostMapping("/cargas") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> carga(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   ScCarga c = new ScCarga();
   c.setEmpresaId(u.getEmpresaId());
   c.setCodigo(MapValues.required(b, "codigo"));
   c.setTransportadoraId(MapValues.lng(b.get("transportadoraId")));
   c.setVeiculo(MapValues.str(b.get("veiculo")));
   c.setOrigem(MapValues.str(b.get("origem"))); c.setDestino(MapValues.str(b.get("destino")));
   c.setPeso(MapValues.bd(b.getOrDefault("peso",0))); c.setVolume(MapValues.bd(b.getOrDefault("volume",0)));
   c.setValorMercadoria(MapValues.bd(b.getOrDefault("valorMercadoria",0)));
   c.setRotaId(MapValues.lng(b.get("rotaId")));
   c.setFreteEstimado(MapValues.bd(b.getOrDefault("freteEstimado",0)));
   cargaRepository.save(c);
   return Map.of("ok",true);
 }
 @PostMapping("/cargas/{id}/tracking") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> tracking(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id,@RequestBody Map<String,Object>b){
   ScTracking t = new ScTracking();
   t.setEmpresaId(u.getEmpresaId()); t.setCargaId(id);
   t.setEvento(MapValues.required(b, "evento"));
   t.setLocalizacao(MapValues.str(b.get("localizacao")));
   t.setLatitude(MapValues.bd(b.get("latitude"))); t.setLongitude(MapValues.bd(b.get("longitude")));
   t.setOcorrencia(MapValues.str(b.get("ocorrencia"))); t.setComprovante(MapValues.str(b.get("comprovante")));
   trackingRepository.save(t);
   return Map.of("ok",true);
 }
 @GetMapping("/cargas/{id}/tracking") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> tracking(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
   return trackingRepository.findByEmpresaIdAndCargaIdOrderByDataEventoAsc(u.getEmpresaId(),id).stream().map(t -> {
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("id", t.getId()); m.put("empresa_id", t.getEmpresaId()); m.put("carga_id", t.getCargaId());
     m.put("evento", t.getEvento()); m.put("data_evento", t.getDataEvento());
     m.put("localizacao", t.getLocalizacao()); m.put("latitude", t.getLatitude());
     m.put("longitude", t.getLongitude()); m.put("ocorrencia", t.getOcorrencia());
     m.put("comprovante", t.getComprovante());
     return m;
   }).toList();
 }
 @PostMapping("/fretes/{cargaId}/conferir") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> frete(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long cargaId,@RequestBody Map<String,Object>b){
   Long e = u.getEmpresaId();
   ScFrete f = freteRepository.findByEmpresaIdAndCargaId(e, cargaId).orElseGet(ScFrete::new);
   f.setEmpresaId(e); f.setCargaId(cargaId);
   f.setTransportadoraId(MapValues.lng(b.get("transportadoraId")));
   f.setDocumento(MapValues.str(b.get("documento")));
   f.setFrete(MapValues.bd(b.getOrDefault("frete",0)));
   f.setPedagio(MapValues.bd(b.getOrDefault("pedagio",0)));
   f.setAdicionais(MapValues.bd(b.getOrDefault("adicionais",0)));
   f.setTotal(MapValues.bd(b.getOrDefault("total",0)));
   f.setStatus("CONFERIDO");
   f.setConferidoEm(LocalDateTime.now());
   freteRepository.save(f);
   return Map.of("ok",true);
 }
}
