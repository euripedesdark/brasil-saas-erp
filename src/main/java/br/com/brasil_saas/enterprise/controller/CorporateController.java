package br.com.brasil_saas.enterprise.controller;

import br.com.brasil_saas.enterprise.model.GovControle;
import br.com.brasil_saas.enterprise.model.GovRisco;
import br.com.brasil_saas.enterprise.repository.GovControleRepository;
import br.com.brasil_saas.enterprise.repository.GovRiscoRepository;
import br.com.brasil_saas.enterprise.service.IntercompanyService;
import br.com.brasil_saas.financeiro.model.FinConsolidacao;
import br.com.brasil_saas.financeiro.model.IntercompanyLancamento;
import br.com.brasil_saas.financeiro.repository.FinConsolidacaoRepository;
import br.com.brasil_saas.financeiro.repository.IntercompanyLancamentoRepository;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/corporativo")
@RequiredArgsConstructor
public class CorporateController {
 private final IntercompanyLancamentoRepository intercompanyRepository;
 private final FinConsolidacaoRepository consolidacaoRepository;
 private final GovRiscoRepository riscoRepository;
 private final GovControleRepository controleRepository;
 private final IntercompanyService intercompanyService;
 private static final Set<String> RECURSOS = Set.of(
  "intercompany","consolidacoes","riscos","controles");
 private static final Set<String> BLOQUEADOS = Set.of(
  "id","uuid","empresa_id","created_at","created_by","updated_at","updated_by","deleted_at");
 @GetMapping("/{resource}") @PreAuthorize("hasAuthority('corporativo:leitura')")
 public List<Map<String,Object>> listar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource){
  validar(resource);
  Long empresa = u.getEmpresaId();
  return switch (resource) {
    case "intercompany" -> intercompanyRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(IntercompanyLancamento::toMap).toList();
    case "consolidacoes" -> consolidacaoRepository.findByEmpresaIdOrderByIdDesc(empresa)
      .stream().map(FinConsolidacao::toMap).toList();
    case "riscos" -> riscoRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(GovRisco::toMap).toList();
    case "controles" -> controleRepository.findByEmpresaIdAndDeletedAtIsNullOrderByIdDesc(empresa)
      .stream().map(GovControle::toMap).toList();
    default -> throw new IllegalArgumentException("Recurso corporativo inválido");
  };
 }
 @PostMapping("/{resource}") @PreAuthorize("hasAuthority('corporativo:escrita')")
 @Transactional
 public Map<String,Object> criar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable String resource,@RequestBody Map<String,Object> body){
  validar(resource);
  if (!temCampoUtil(body)) {
    throw new IllegalArgumentException("Nenhum campo informado");
  }
  Long empresa = u.getEmpresaId();
  return switch (resource) {
    case "intercompany" -> {
      IntercompanyLancamento e = new IntercompanyLancamento();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield intercompanyRepository.save(e).toMap();
    }
    case "consolidacoes" -> {
      FinConsolidacao e = new FinConsolidacao();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield consolidacaoRepository.save(e).toMap();
    }
    case "riscos" -> {
      GovRisco e = new GovRisco();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield riscoRepository.save(e).toMap();
    }
    case "controles" -> {
      GovControle e = new GovControle();
      e.setEmpresaId(empresa); e.setCreatedBy(u.getId()); e.aplicar(body);
      yield controleRepository.save(e).toMap();
    }
    default -> throw new IllegalArgumentException("Recurso corporativo inválido");
  };
 }
 @PostMapping("/consolidacoes/{id}/executar") @PreAuthorize("hasAuthority('corporativo:escrita')")
 @Transactional
 public Map<String,Object> executar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  FinConsolidacao c = consolidacaoRepository.findByIdAndEmpresaId(id, u.getEmpresaId())
    .orElseThrow(() -> new NoSuchElementException("Consolidação não encontrada"));
  c.setStatus("CONSOLIDADO");
  c.setConsolidadoEm(LocalDateTime.now());
  c.setConsolidadoPor(u.getId());
  c.setUpdatedBy(u.getId());
  return consolidacaoRepository.save(c).toMap();
 }
 @PostMapping("/intercompany/{id}/reconciliar") @PreAuthorize("hasAuthority('corporativo:escrita')")
 public Map<String,Object> reconciliar(@AuthenticationPrincipal AuthenticatedUser u,@PathVariable Long id){
  return intercompanyService.reconciliar(u.getEmpresaId(),u.getId(),id);
 }
 @GetMapping("/intercompany/eliminacoes") @PreAuthorize("hasAuthority('corporativo:leitura')")
 public Map<String,Object> eliminacoes(@AuthenticationPrincipal AuthenticatedUser u,@RequestParam java.time.LocalDate competencia){
  return Map.of("competencia",competencia,"eliminacoes",intercompanyService.eliminacoes(u.getEmpresaId(),competencia));
 }
 private void validar(String r){
   if(!RECURSOS.contains(r)) throw new IllegalArgumentException("Recurso corporativo inválido");
 }

 private boolean temCampoUtil(Map<String,Object> body) {
   if (body == null || body.isEmpty()) {
     return false;
   }
   return body.keySet().stream()
     .anyMatch(k -> k != null && k.matches("[a-z][a-z0-9_]*") && !BLOQUEADOS.contains(k));
 }
}
