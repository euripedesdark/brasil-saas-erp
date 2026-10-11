package br.com.brasil_saas.contabilidade.controller;
import br.com.brasil_saas.contabilidade.model.*;
import br.com.brasil_saas.contabilidade.service.ContabilidadeService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/contabilidade") @RequiredArgsConstructor
public class ContabilidadeController {
    private final ContabilidadeService svc;
    public record GerarTituloReq(Long contaDebitoId, Long contaCreditoId) {}
    public record ApurarReq(int exercicio, Long contaLucrosAcumuladosId) {}
    public record EstornoReq(String motivo) {}
    @GetMapping("/lancamentos") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<CtbLancamento> lancamentos(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam(required = false) String periodo, @RequestParam(required = false) String status) {
        return svc.lancamentos(u.getEmpresaId(), periodo, status);
    }
    @PostMapping("/lancamentos") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<CtbLancamento> salvar(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody CtbLancamento l) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.salvar(u.getEmpresaId(), l));
    }
    @GetMapping("/lancamentos/{id}/partidas") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<CtbPartida> partidas(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        return svc.partidas(u.getEmpresaId(), id);
    }
    @PostMapping("/lancamentos/{id}/partidas") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<CtbPartida> addPartida(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @Valid @RequestBody CtbPartida p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.addPartida(u.getEmpresaId(), id, p));
    }
    @DeleteMapping("/lancamentos/{lid}/partidas/{pid}") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<Void> removerPartida(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long lid, @PathVariable Long pid) {
        svc.removerPartida(u.getEmpresaId(), lid, pid);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/lancamentos/{id}/lancar") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public CtbLancamento lancar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        return svc.lancar(u.getEmpresaId(), id);
    }
    @PostMapping("/lancamentos/{id}/estornar") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public CtbLancamento estornar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id, @RequestBody(required = false) EstornoReq r) {
        return svc.estornar(u.getEmpresaId(), id, r == null ? null : r.motivo());
    }
    @PostMapping("/lancamentos/gerar-titulo/{tituloId}") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<CtbLancamento> gerarDeTitulo(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long tituloId, @RequestBody GerarTituloReq r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.gerarDeTitulo(u.getEmpresaId(), u.getId(), tituloId, r.contaDebitoId(), r.contaCreditoId()));
    }
    @PostMapping("/encerramento/apurar") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<CtbLancamento> apurar(@AuthenticationPrincipal AuthenticatedUser u, @Valid @RequestBody ApurarReq r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(svc.apurarResultado(u.getEmpresaId(), u.getId(), r.exercicio(), r.contaLucrosAcumuladosId()));
    }

    @GetMapping("/razao") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<Map<String, Object>> razao(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam Long contaId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return svc.razao(u.getEmpresaId(), contaId, de, ate);
    }
    @GetMapping("/balancete") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<Map<String, Object>> balancete(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return svc.balancete(u.getEmpresaId(), de, ate);
    }
    @GetMapping("/dre") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<Map<String, Object>> dre(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam int exercicio) {
        return svc.dre(u.getEmpresaId(), exercicio);
    }
    @GetMapping("/balanco") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public Map<String, Object> balanco(@AuthenticationPrincipal AuthenticatedUser u, @RequestParam int exercicio) {
        return svc.balanco(u.getEmpresaId(), exercicio);
    }
    @GetMapping("/fechamentos") @PreAuthorize("hasAuthority('contabilidade:leitura')")
    public List<CtbFechamento> fechamentos(@AuthenticationPrincipal AuthenticatedUser u) {
        return svc.fechamentos(u.getEmpresaId());
    }
    @PostMapping("/fechamentos/{periodo}/fechar") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public CtbFechamento fechar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String periodo) {
        return svc.fechar(u.getEmpresaId(), u.getId(), periodo);
    }
    @PostMapping("/fechamentos/{periodo}/reabrir") @PreAuthorize("hasAuthority('contabilidade:escrita')")
    public ResponseEntity<Void> reabrir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable String periodo) {
        svc.reabrir(u.getEmpresaId(), periodo);
        return ResponseEntity.noContent().build();
    }
}
