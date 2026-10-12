package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.PlanoContas;
import br.com.brasil_saas.financeiro.repository.PlanoContasRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/financeiro/plano-contas") @RequiredArgsConstructor
public class PlanoContasController {
    private final PlanoContasRepository repo;
    @GetMapping @PreAuthorize("hasAuthority('financeiro:planocontas:leitura')")
    public List<PlanoContasResponse> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndAtivaTrueAndDeletedAtIsNullOrderByCodigo(u.getEmpresaId()).stream()
            .map(p -> new PlanoContasResponse(p.getId(), p.getCodigo(), p.getDescricao(), p.getTipo(),
                p.getNatureza(), p.getContaPaiId(), p.getNivel(), p.getAtiva())).toList();
    }
    @PostMapping @PreAuthorize("hasAuthority('financeiro:planocontas:escrita')")
    public ResponseEntity<PlanoContasResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                                     @Valid @RequestBody PlanoContasRequest r) {
        PlanoContas p = new PlanoContas();
        p.setEmpresaId(u.getEmpresaId());
        p.setCodigo(r.codigo()); p.setDescricao(r.descricao()); p.setTipo(r.tipo()); p.setNatureza(r.natureza());
        p.setContaPaiId(r.contaPaiId());
        p.setNivel(r.nivel() == null ? 1 : r.nivel());
        p.setAtiva(r.ativa() == null || r.ativa());
        p = repo.save(p);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new PlanoContasResponse(p.getId(), p.getCodigo(), p.getDescricao(), p.getTipo(),
                p.getNatureza(), p.getContaPaiId(), p.getNivel(), p.getAtiva()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:planocontas:escrita')")
    public ResponseEntity<PlanoContasResponse> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody PlanoContasRequest r) {
        PlanoContas p = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta do plano não encontrada"));
        if (r.codigo() != null) p.setCodigo(r.codigo());
        if (r.descricao() != null) p.setDescricao(r.descricao());
        if (r.tipo() != null) p.setTipo(r.tipo());
        if (r.natureza() != null) p.setNatureza(r.natureza());
        p.setContaPaiId(r.contaPaiId());
        if (r.nivel() != null) p.setNivel(r.nivel());
        if (r.ativa() != null) p.setAtiva(r.ativa());
        p = repo.save(p);
        return ResponseEntity.ok(new PlanoContasResponse(p.getId(), p.getCodigo(), p.getDescricao(), p.getTipo(),
            p.getNatureza(), p.getContaPaiId(), p.getNivel(), p.getAtiva()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:planocontas:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        PlanoContas p = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta do plano não encontrada"));
        p.setAtiva(false);
        repo.save(p);
        return ResponseEntity.noContent().build();
    }
}
