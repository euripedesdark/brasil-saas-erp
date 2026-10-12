package br.com.brasil_saas.rh.controller;

import br.com.brasil_saas.rh.model.Ponto;
import br.com.brasil_saas.rh.service.PontoService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;
import java.util.List;
@RestController @RequestMapping("/api/rh/ponto") @RequiredArgsConstructor
public class PontoController {
    private final PontoService svc;
    public record AjusteReq(LocalTime e1, LocalTime s1, LocalTime e2, LocalTime s2, String observacao) {}
    public record FaltaReq(java.time.LocalDate data, String observacao) {}
    @GetMapping @PreAuthorize("hasAuthority('rh:funcionario:leitura')")
    public List<Ponto> espelho(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Long funcionarioId, @RequestParam(required = false) Integer ano, @RequestParam(required = false) Integer mes) { return svc.espelho(u.getEmpresaId(), funcionarioId, ano, mes); }
    @PostMapping("/bater") @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    public Ponto bater(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Long funcionarioId) { return svc.bater(u.getEmpresaId(), funcionarioId); }
    @PostMapping("/{id}/ajustar") @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    public Ponto ajustar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @RequestBody AjusteReq r) { return svc.ajustar(u.getEmpresaId(), id, r.e1(), r.s1(), r.e2(), r.s2(), r.observacao()); }
    @PostMapping("/falta") @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    public ResponseEntity<Ponto> falta(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Long funcionarioId, @RequestBody(required = false) FaltaReq r) { return ResponseEntity.status(HttpStatus.CREATED).body(svc.falta(u.getEmpresaId(), funcionarioId, r == null ? null : r.data(), r == null ? null : r.observacao())); }
}
