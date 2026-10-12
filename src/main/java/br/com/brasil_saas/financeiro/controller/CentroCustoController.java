package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.CentroCusto;
import br.com.brasil_saas.financeiro.repository.CentroCustoRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/financeiro/centros-custo") @RequiredArgsConstructor
public class CentroCustoController {
    private final CentroCustoRepository repo;
    @GetMapping @PreAuthorize("hasAuthority('financeiro:centrocusto:leitura')")
    public List<CentroCustoResponse> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndAtivoTrueAndDeletedAtIsNullOrderByCodigo(u.getEmpresaId()).stream()
            .map(c -> new CentroCustoResponse(c.getId(), c.getCodigo(), c.getDescricao(),
                c.getCentroCustoPaiId(), c.getAtivo())).toList();
    }
    @PostMapping @PreAuthorize("hasAuthority('financeiro:centrocusto:escrita')")
    public ResponseEntity<CentroCustoResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                                     @Valid @RequestBody CentroCustoRequest r) {
        CentroCusto c = new CentroCusto();
        c.setEmpresaId(u.getEmpresaId());
        c.setCodigo(r.codigo()); c.setDescricao(r.descricao()); c.setCentroCustoPaiId(r.centroCustoPaiId());
        c.setAtivo(r.ativo() == null || r.ativo());
        c = repo.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new CentroCustoResponse(c.getId(), c.getCodigo(), c.getDescricao(),
                c.getCentroCustoPaiId(), c.getAtivo()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:centrocusto:escrita')")
    public ResponseEntity<CentroCustoResponse> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody CentroCustoRequest r) {
        CentroCusto c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Centro de custo não encontrado"));
        if (r.codigo() != null) c.setCodigo(r.codigo());
        if (r.descricao() != null) c.setDescricao(r.descricao());
        c.setCentroCustoPaiId(r.centroCustoPaiId());
        if (r.ativo() != null) c.setAtivo(r.ativo());
        c = repo.save(c);
        return ResponseEntity.ok(new CentroCustoResponse(c.getId(), c.getCodigo(), c.getDescricao(),
            c.getCentroCustoPaiId(), c.getAtivo()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:centrocusto:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        CentroCusto c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Centro de custo não encontrado"));
        c.setAtivo(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }
}
