package br.com.brasil_saas.producao.controller;
import br.com.brasil_saas.producao.model.MpsItem;
import br.com.brasil_saas.producao.service.MpsService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/producao/mps") @RequiredArgsConstructor
public class MpsController {
    private final MpsService svc;
    @GetMapping @PreAuthorize("hasAuthority('producao:mps:leitura')")
    public List<MpsItem> listar(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String periodo) { return svc.listar(u.getEmpresaId(), periodo); }
    @PostMapping("/gerar") @PreAuthorize("hasAuthority('producao:mps:escrita')")
    public List<MpsItem> gerar(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam String periodo) { return svc.gerar(u.getEmpresaId(), periodo); }
    @PostMapping("/{id}/confirmar") @PreAuthorize("hasAuthority('producao:mps:escrita')")
    public MpsItem confirmar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { return svc.confirmar(u.getEmpresaId(), id); }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('producao:mps:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { svc.excluir(u.getEmpresaId(), id); return ResponseEntity.noContent().build(); }
}
