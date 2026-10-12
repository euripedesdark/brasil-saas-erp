package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.TipoPagamento;
import br.com.brasil_saas.financeiro.repository.TipoPagamentoRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/financeiro/tipos-pagamento") @RequiredArgsConstructor
public class TipoPagamentoController {
    private final TipoPagamentoRepository repo;
    @GetMapping @PreAuthorize("hasAuthority('financeiro:tipopagamento:leitura')")
    public List<TipoPagamentoResponse> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByDescricao(u.getEmpresaId()).stream()
            .map(t -> new TipoPagamentoResponse(t.getId(), t.getDescricao(), t.getCodigo(), t.getAtivo())).toList();
    }
    @PostMapping @PreAuthorize("hasAuthority('financeiro:tipopagamento:escrita')")
    public ResponseEntity<TipoPagamentoResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                                       @Valid @RequestBody TipoPagamentoRequest r) {
        TipoPagamento t = new TipoPagamento();
        t.setEmpresaId(u.getEmpresaId());
        t.setDescricao(r.descricao()); t.setCodigo(r.codigo());
        t.setAtivo(r.ativo() == null || r.ativo());
        t = repo.save(t);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new TipoPagamentoResponse(t.getId(), t.getDescricao(), t.getCodigo(), t.getAtivo()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:tipopagamento:escrita')")
    public ResponseEntity<TipoPagamentoResponse> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody TipoPagamentoRequest r) {
        TipoPagamento t = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Tipo de pagamento não encontrado"));
        if (r.descricao() != null) t.setDescricao(r.descricao());
        if (r.codigo() != null) t.setCodigo(r.codigo());
        if (r.ativo() != null) t.setAtivo(r.ativo());
        t = repo.save(t);
        return ResponseEntity.ok(new TipoPagamentoResponse(t.getId(), t.getDescricao(), t.getCodigo(), t.getAtivo()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:tipopagamento:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        TipoPagamento t = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Tipo de pagamento não encontrado"));
        t.setAtivo(false);
        repo.save(t);
        return ResponseEntity.noContent().build();
    }
}
