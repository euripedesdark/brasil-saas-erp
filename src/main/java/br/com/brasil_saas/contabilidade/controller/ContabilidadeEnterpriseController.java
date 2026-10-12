package br.com.brasil_saas.contabilidade.controller;
import br.com.brasil_saas.contabilidade.model.ContRateio;
import br.com.brasil_saas.contabilidade.model.FechamentoCheck;
import br.com.brasil_saas.contabilidade.model.PeriodoContabilEnt;
import br.com.brasil_saas.contabilidade.model.RegraLancamento;
import br.com.brasil_saas.contabilidade.repository.ContRateioRepository;
import br.com.brasil_saas.contabilidade.repository.FechamentoCheckRepository;
import br.com.brasil_saas.contabilidade.repository.PeriodoContabilEntRepository;
import br.com.brasil_saas.contabilidade.repository.RegraLancamentoRepository;
import br.com.brasil_saas.financeiro.model.LancamentoContabil;
import br.com.brasil_saas.financeiro.model.LancamentoPartida;
import br.com.brasil_saas.financeiro.model.PlanoContas;
import br.com.brasil_saas.financeiro.repository.LancamentoContabilRepository;
import br.com.brasil_saas.financeiro.repository.LancamentoPartidaRepository;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/contabilidade/enterprise") @RequiredArgsConstructor
public class ContabilidadeEnterpriseController {
 private final RegraLancamentoRepository regraRepository;
 private final ContRateioRepository rateioRepository;
 private final FechamentoCheckRepository fechamentoCheckRepository;
 private final PeriodoContabilEntRepository periodoRepository;
 private final LancamentoContabilRepository contabilRepository;
 private final LancamentoPartidaRepository partidaRepository;
 private final PlanoContasRepository planoContasRepository;

 @GetMapping("/regras") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> regras(@AuthenticationPrincipal AuthenticatedUser u){
   return regraRepository.findByEmpresaIdOrderByCodigo(u.getEmpresaId()).stream().map(ContabilidadeEnterpriseController::regraMap).toList();
 }
 @PostMapping("/regras") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> regra(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   Long empresa = u.getEmpresaId();
   String codigo = required(b, "codigo");
   RegraLancamento r = regraRepository.findByEmpresaIdAndCodigo(empresa, codigo).orElseGet(RegraLancamento::new);
   r.setEmpresaId(empresa);
   r.setCodigo(codigo);
   r.setNome(required(b, "nome"));
   r.setOrigem(required(b, "origem"));
   r.setContaDebitoId(lng(b.get("contaDebitoId")));
   r.setContaCreditoId(lng(b.get("contaCreditoId")));
   r.setCentroCustoId(lng(b.get("centroCustoId")));
   r.setHistorico(str(b.get("historico")));
   Object cfg = b.getOrDefault("configuracao", "{}");
   r.setConfiguracao(cfg == null ? "{}" : String.valueOf(cfg));
   regraRepository.save(r);
   return Map.of("ok",true);
 }
 @PostMapping("/lancamentos/{id}/validar") @PreAuthorize("hasAuthority('enterprise:escrita')") public Map<String,Object> validar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
   LancamentoContabil l = contabilRepository.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
     .orElseThrow(() -> new NoSuchElementException("Lançamento não encontrado"));
   BigDecimal debitos = BigDecimal.ZERO, creditos = BigDecimal.ZERO;
   for (LancamentoPartida p : partidaRepository.findByLancamentoIdAndDeletedAtIsNull(l.getId())) {
     if ("D".equals(p.getTipo())) debitos = debitos.add(nz(p.getValor()));
     else if ("C".equals(p.getTipo())) creditos = creditos.add(nz(p.getValor()));
   }
   Map<String,Object> x = new LinkedHashMap<>();
   x.put("debitos", debitos); x.put("creditos", creditos);
   boolean ok=Objects.equals(String.valueOf(x.get("debitos")),String.valueOf(x.get("creditos"))); return Map.of("ok",ok,"lancamentoId",id,"totais",x);
 }
 @GetMapping("/balancete") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> balancete(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam Long periodoId){
   Long empresa = u.getEmpresaId();
   Map<Long, BigDecimal[]> tot = new HashMap<>();
   for (Object[] row : partidaRepository.partidasParaBalancete(empresa, periodoId)) {
     LancamentoPartida p = (LancamentoPartida) row[0];
     BigDecimal[] t = tot.computeIfAbsent(p.getPlanoContasId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
     if ("D".equals(p.getTipo())) t[0] = t[0].add(nz(p.getValor()));
     else if ("C".equals(p.getTipo())) t[1] = t[1].add(nz(p.getValor()));
   }
   List<Map<String,Object>> out = new ArrayList<>();
   for (PlanoContas p : planoContasRepository.findByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresa)) {
     BigDecimal[] t = tot.getOrDefault(p.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("conta_id", p.getId()); m.put("codigo", p.getCodigo()); m.put("descricao", p.getDescricao());
     m.put("debito", t[0]); m.put("credito", t[1]); m.put("saldo", t[0].subtract(t[1]));
     out.add(m);
   }
   return out;
 }
 @GetMapping("/dre") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> dre(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam Long periodoId){
   Long empresa = u.getEmpresaId();
   Map<Long, BigDecimal> saldos = new HashMap<>();
   for (LancamentoPartida p : partidaRepository.partidasDoPeriodo(empresa, periodoId)) {
     BigDecimal v = "C".equals(p.getTipo()) ? nz(p.getValor()) : nz(p.getValor()).negate();
     saldos.merge(p.getPlanoContasId(), v, BigDecimal::add);
   }
   List<Map<String,Object>> out = new ArrayList<>();
   for (PlanoContas p : planoContasRepository.findByEmpresaIdAndDeletedAtIsNullOrderByCodigo(empresa)) {
     if (!saldos.containsKey(p.getId())) continue;
     Map<String,Object> m = new LinkedHashMap<>();
     m.put("codigo", p.getCodigo()); m.put("descricao", p.getDescricao()); m.put("saldo", saldos.get(p.getId()));
     out.add(m);
   }
   return out;
 }
 @PostMapping("/periodos/{id}/fechar") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> fechar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
   Long empresa=u.getEmpresaId();
   PeriodoContabilEnt p = periodoRepository.findByIdAndEmpresaId(id, empresa)
     .orElseThrow(() -> new NoSuchElementException("Período inexistente ou já fechado"));
   if ("FECHADO".equals(p.getStatus())) throw new NoSuchElementException("Período inexistente ou já fechado");
   p.setStatus("FECHADO");
   p.setFechamentoContabil(true);
   p.setFechadoEm(LocalDateTime.now());
   p.setFechadoPor(u.getId());
   p.setUpdatedBy(u.getId());
   periodoRepository.save(p);
   return periodoMap(p);
 }
 @GetMapping("/fechamento/{periodoId}") @PreAuthorize("hasAuthority('enterprise:leitura')") public List<Map<String,Object>> fechamento(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long periodoId){
   return fechamentoCheckRepository.findByEmpresaIdAndPeriodoIdOrderById(u.getEmpresaId(), periodoId)
     .stream().map(ContabilidadeEnterpriseController::checkMap).toList();
 }
 @PostMapping("/rateio") @PreAuthorize("hasAuthority('enterprise:escrita')") @Transactional public Map<String,Object> rateio(@AuthenticationPrincipal AuthenticatedUser u,@RequestBody Map<String,Object>b){
   ContRateio r = new ContRateio();
   r.setEmpresaId(u.getEmpresaId());
   r.setLancamentoId(lng(b.get("lancamentoId")));
   r.setCentroCustoId(lng(b.get("centroCustoId")));
   r.setPlanoContasId(lng(b.get("planoContasId")));
   r.setPercentual(bd(b.get("percentual")));
   r.setValor(bd(b.get("valor")));
   r.setCompetencia(data(b.get("competencia")));
   rateioRepository.save(r);
   return Map.of("ok",true);
 }

 private static Map<String,Object> regraMap(RegraLancamento r) {
   Map<String,Object> m = new LinkedHashMap<>();
   m.put("id", r.getId()); m.put("empresa_id", r.getEmpresaId()); m.put("codigo", r.getCodigo());
   m.put("nome", r.getNome()); m.put("origem", r.getOrigem());
   m.put("conta_debito_id", r.getContaDebitoId()); m.put("conta_credito_id", r.getContaCreditoId());
   m.put("centro_custo_id", r.getCentroCustoId()); m.put("historico", r.getHistorico());
   m.put("ativo", r.getAtivo()); m.put("configuracao", r.getConfiguracao());
   m.put("created_at", r.getCreatedAt()); m.put("updated_at", r.getUpdatedAt());
   return m;
 }

 private static Map<String,Object> periodoMap(PeriodoContabilEnt p) {
   Map<String,Object> m = new LinkedHashMap<>();
   m.put("id", p.getId()); m.put("uuid", p.getUuid()); m.put("empresa_id", p.getEmpresaId());
   m.put("competencia", p.getCompetencia()); m.put("status", p.getStatus());
   m.put("fechamento_financeiro", p.getFechamentoFinanceiro());
   m.put("fechamento_fiscal", p.getFechamentoFiscal());
   m.put("fechamento_contabil", p.getFechamentoContabil());
   m.put("observacao", p.getObservacao());
   m.put("fechado_em", p.getFechadoEm()); m.put("fechado_por", p.getFechadoPor());
   m.put("created_at", p.getCreatedAt()); m.put("updated_at", p.getUpdatedAt());
   m.put("created_by", p.getCreatedBy()); m.put("updated_by", p.getUpdatedBy());
   return m;
 }

 private static Map<String,Object> checkMap(FechamentoCheck c) {
   Map<String,Object> m = new LinkedHashMap<>();
   m.put("id", c.getId()); m.put("empresa_id", c.getEmpresaId()); m.put("periodo_id", c.getPeriodoId());
   m.put("etapa", c.getEtapa()); m.put("codigo", c.getCodigo()); m.put("descricao", c.getDescricao());
   m.put("status", c.getStatus()); m.put("quantidade", c.getQuantidade());
   m.put("executado_em", c.getExecutadoEm()); m.put("executado_por", c.getExecutadoPor());
   m.put("observacao", c.getObservacao());
   return m;
 }

 private static String required(Map<String,Object> b, String k) {
   Object v = b.get(k);
   if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException("Campo obrigatório: " + k);
   return String.valueOf(v);
 }

 private static String str(Object v) { return v == null ? null : String.valueOf(v); }

 private static Long lng(Object v) {
   if (v == null) return null;
   if (v instanceof Number n) return n.longValue();
   String s = String.valueOf(v).trim();
   return s.isEmpty() ? null : Long.valueOf(s);
 }

 private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

 private static BigDecimal bd(Object v) {
   if (v == null) return BigDecimal.ZERO;
   if (v instanceof BigDecimal b) return b;
   if (v instanceof Number n) return new BigDecimal(n.toString());
   String s = String.valueOf(v).trim();
   return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
 }

 private static LocalDate data(Object v) {
   if (v == null) return null;
   if (v instanceof LocalDate d) return d;
   if (v instanceof java.sql.Date d) return d.toLocalDate();
   if (v instanceof java.util.Date d) return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
   return LocalDate.parse(String.valueOf(v).trim().substring(0, 10));
 }
}
