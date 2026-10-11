package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.service.AprovacaoTituloService;
import br.com.brasil_saas.financeiro.service.TituloService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate; import java.util.List;

@RestController @RequestMapping("/api/financeiro/titulos") @RequiredArgsConstructor
public class TituloController {
    private final TituloService service;
    private final AprovacaoTituloService aprovacaoService;

    @GetMapping @PreAuthorize("hasAuthority('financeiro:titulo:leitura')")
    public List<TituloResponse> listar(@AuthenticationPrincipal AuthenticatedUser u,
                                       @RequestParam(required = false) String status) {
        return service.listar(u.getEmpresaId(), status);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:titulo:leitura')")
    public TituloResponse buscar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        return service.buscar(u.getEmpresaId(), id);
    }
    @GetMapping("/{id}/parcelas") @PreAuthorize("hasAuthority('financeiro:titulo:leitura')")
    public List<ParcelaResponse> parcelas(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        return service.parcelas(u.getEmpresaId(), id);
    }
    @PostMapping("/{id}/parcelas") @PreAuthorize("hasAuthority('financeiro:titulo:escrita')")
    public List<ParcelaResponse> gerarParcelas(@AuthenticationPrincipal AuthenticatedUser u,
                                               @PathVariable Long id,
                                               @RequestParam(required = false) Long condicaoPagamentoId) {
        return service.gerarParcelas(u.getEmpresaId(), id, condicaoPagamentoId);
    }
    @PostMapping("/{id}/baixar") @PreAuthorize("hasAuthority('financeiro:titulo:baixar')")
    public BaixaResponse baixar(@AuthenticationPrincipal AuthenticatedUser u,
                                @PathVariable Long id, @Valid @RequestBody BaixaRequest request) {
        return service.baixar(u.getEmpresaId(), id, request);
    }
    @GetMapping("/vencimentos") @PreAuthorize("hasAuthority('financeiro:titulo:leitura')")
    public List<ParcelaResponse> vencimentos(@AuthenticationPrincipal AuthenticatedUser u,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.vencimentos(u.getEmpresaId(), inicio, fim);
    }

    // ===== Workflow de aprovação (Fase 1 — Relatório Paridade 25/09/2026) =====

    @PostMapping("/{id}/aprovacao/solicitar") @PreAuthorize("hasAuthority('financeiro:titulo:escrita')")
    public List<AprovacaoResponse> solicitarAprovacao(@AuthenticationPrincipal AuthenticatedUser u,
                                                      @PathVariable Long id,
                                                      @RequestBody SolicitacaoAprovacaoRequest request) {
        return aprovacaoService.solicitar(u.getEmpresaId(), u.getId(), id, request);
    }

    @PostMapping("/aprovacoes/{aprovacaoId}/aprovar") @PreAuthorize("hasAuthority('financeiro:titulo:aprovar')")
    public AprovacaoResponse aprovar(@AuthenticationPrincipal AuthenticatedUser u,
                                     @PathVariable Long aprovacaoId,
                                     @RequestBody(required = false) DecisaoAprovacaoRequest request) {
        return aprovacaoService.aprovar(u.getEmpresaId(), u.getId(), aprovacaoId, request);
    }

    @PostMapping("/aprovacoes/{aprovacaoId}/rejeitar") @PreAuthorize("hasAuthority('financeiro:titulo:aprovar')")
    public AprovacaoResponse rejeitar(@AuthenticationPrincipal AuthenticatedUser u,
                                      @PathVariable Long aprovacaoId,
                                      @RequestBody(required = false) DecisaoAprovacaoRequest request) {
        return aprovacaoService.rejeitar(u.getEmpresaId(), u.getId(), aprovacaoId, request);
    }

    @GetMapping("/aprovacoes/pendentes") @PreAuthorize("hasAuthority('financeiro:titulo:aprovacao-leitura')")
    public List<AprovacaoResponse> aprovacoesPendentes(@AuthenticationPrincipal AuthenticatedUser u) {
        return aprovacaoService.pendentes(u.getEmpresaId(), u.getId());
    }

    @GetMapping("/{id}/aprovacoes") @PreAuthorize("hasAuthority('financeiro:titulo:aprovacao-leitura')")
    public List<AprovacaoResponse> aprovacoesDoTitulo(@AuthenticationPrincipal AuthenticatedUser u,
                                                      @PathVariable Long id) {
        return aprovacaoService.porTitulo(u.getEmpresaId(), id);
    }
}
