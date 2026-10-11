package br.com.brasil_saas.crm.controller;
import br.com.brasil_saas.crm.model.*;
import br.com.brasil_saas.crm.service.CrmService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/crm") @RequiredArgsConstructor
public class CrmController {
    private final CrmService svc;
    public record EtapaReq(String etapa) {}
    @GetMapping("/leads") @PreAuthorize("hasAuthority('crm:leitura')")
    public List<CrmLead> leads(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String etapa, @RequestParam(required = false) String status) { return svc.leads(u.getEmpresaId(), etapa, status); }
    @PostMapping("/leads") @PreAuthorize("hasAuthority('crm:escrita')")
    public ResponseEntity<CrmLead> salvar(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody CrmLead l) { return ResponseEntity.status(HttpStatus.CREATED).body(svc.salvar(u.getEmpresaId(), l)); }
    @PostMapping("/leads/{id}/etapa") @PreAuthorize("hasAuthority('crm:escrita')")
    public CrmLead mover(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @RequestBody EtapaReq r) { return svc.moverEtapa(u.getEmpresaId(), id, r.etapa()); }
    public record GerarPedidoReq(Long clienteId) {}
    @PostMapping("/leads/{id}/gerar-pedido") @PreAuthorize("hasAuthority('crm:escrita') and hasAuthority('vendas:pedido:escrita')")
    public Map<String, Object> gerarPedido(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @RequestBody GerarPedidoReq r) { return svc.gerarPedido(u.getEmpresaId(), id, r.clienteId()); }
    @DeleteMapping("/leads/{id}") @PreAuthorize("hasAuthority('crm:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { svc.excluirLead(u.getEmpresaId(), id); return ResponseEntity.noContent().build(); }
    @GetMapping("/pipeline") @PreAuthorize("hasAuthority('crm:leitura')")
    public List<Map<String, Object>> pipeline(@AuthenticationPrincipal AuthenticatedUser u) { return svc.pipeline(u.getEmpresaId()); }
    @GetMapping("/forecast") @PreAuthorize("hasAuthority('crm:leitura')")
    public Map<String, Object> forecast(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) Integer ano, @RequestParam(required = false) Integer mes) { return svc.forecast(u.getEmpresaId(), ano, mes); }
    @GetMapping("/atividades") @PreAuthorize("hasAuthority('crm:leitura')")
    public List<CrmAtividade> atividades(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) Long leadId, @RequestParam(required = false) Boolean pendentes) { return svc.atividades(u.getEmpresaId(), leadId, pendentes); }
    @PostMapping("/atividades") @PreAuthorize("hasAuthority('crm:escrita')")
    public ResponseEntity<CrmAtividade> salvarAtividade(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody CrmAtividade a) { return ResponseEntity.status(HttpStatus.CREATED).body(svc.salvarAtividade(u.getEmpresaId(), a)); }
    @PostMapping("/atividades/{id}/concluir") @PreAuthorize("hasAuthority('crm:escrita')")
    public CrmAtividade concluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { return svc.concluir(u.getEmpresaId(), id); }
}
