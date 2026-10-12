package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.CondicaoPagamento;
import br.com.brasil_saas.financeiro.repository.CondicaoPagamentoRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/financeiro/condicoes-pagamento") @RequiredArgsConstructor
public class CondicaoPagamentoController {
    private final CondicaoPagamentoRepository repo;
    @GetMapping @PreAuthorize("hasAuthority('financeiro:condicao:leitura')")
    public List<CondicaoResponse> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByDescricao(u.getEmpresaId()).stream()
            .map(c -> new CondicaoResponse(c.getId(), c.getDescricao(), c.getDias(), c.getAtivo())).toList();
    }
    @PostMapping @PreAuthorize("hasAuthority('financeiro:condicao:escrita')")
    public ResponseEntity<CondicaoResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                                  @Valid @RequestBody CondicaoRequest r) {
        CondicaoPagamento c = new CondicaoPagamento();
        c.setEmpresaId(u.getEmpresaId());
        c.setDescricao(r.descricao()); c.setDias(r.dias());
        c.setAtivo(r.ativo() == null || r.ativo());
        c = repo.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CondicaoResponse(c.getId(), c.getDescricao(), c.getDias(), c.getAtivo()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:condicao:escrita')")
    public ResponseEntity<CondicaoResponse> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody CondicaoRequest r) {
        CondicaoPagamento c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Condição não encontrada"));
        if (r.descricao() != null) c.setDescricao(r.descricao());
        if (r.dias() != null) c.setDias(r.dias());
        if (r.ativo() != null) c.setAtivo(r.ativo());
        c = repo.save(c);
        return ResponseEntity.ok(
            new CondicaoResponse(c.getId(), c.getDescricao(), c.getDias(), c.getAtivo()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:condicao:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        CondicaoPagamento c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Condição não encontrada"));
        c.setAtivo(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }
}
