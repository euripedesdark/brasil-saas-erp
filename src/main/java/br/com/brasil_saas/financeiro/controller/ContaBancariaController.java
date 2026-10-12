package br.com.brasil_saas.financeiro.controller;
import br.com.brasil_saas.financeiro.dto.FinanceiroDtos.*;
import br.com.brasil_saas.financeiro.model.ContaBancaria;
import br.com.brasil_saas.financeiro.repository.ContaBancariaRepository;
import br.com.brasil_saas.shared.exception.ResourceNotFoundException;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal; import java.util.List;

@RestController @RequestMapping("/api/financeiro/contas-bancarias") @RequiredArgsConstructor
public class ContaBancariaController {
    private final ContaBancariaRepository repo;
    @GetMapping @PreAuthorize("hasAuthority('financeiro:conta:leitura')")
    public List<ContaResponse> listar(@AuthenticationPrincipal AuthenticatedUser u) {
        return repo.findByEmpresaIdAndAtivaTrueAndDeletedAtIsNullOrderByBanco(u.getEmpresaId()).stream()
            .map(c -> new ContaResponse(c.getId(), c.getBanco(), c.getAgencia(), c.getConta(), c.getDigito(),
                c.getTipo(), c.getSaldoInicial(), c.getAtiva())).toList();
    }
    @PostMapping @PreAuthorize("hasAuthority('financeiro:conta:escrita')")
    public ResponseEntity<ContaResponse> criar(@AuthenticationPrincipal AuthenticatedUser u,
                                               @Valid @RequestBody ContaRequest r) {
        ContaBancaria c = new ContaBancaria();
        c.setEmpresaId(u.getEmpresaId());
        c.setBanco(r.banco()); c.setAgencia(r.agencia()); c.setConta(r.conta()); c.setDigito(r.digito());
        c.setTipo(r.tipo());
        c.setSaldoInicial(r.saldoInicial() == null ? BigDecimal.ZERO : r.saldoInicial());
        c.setAtiva(r.ativa() == null || r.ativa());
        c = repo.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new ContaResponse(c.getId(), c.getBanco(), c.getAgencia(), c.getConta(), c.getDigito(),
                c.getTipo(), c.getSaldoInicial(), c.getAtiva()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:conta:escrita')")
    public ResponseEntity<ContaResponse> atualizar(@AuthenticationPrincipal AuthenticatedUser u,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody ContaRequest r) {
        ContaBancaria c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        if (r.banco() != null) c.setBanco(r.banco());
        if (r.agencia() != null) c.setAgencia(r.agencia());
        if (r.conta() != null) c.setConta(r.conta());
        if (r.digito() != null) c.setDigito(r.digito());
        if (r.tipo() != null) c.setTipo(r.tipo());
        if (r.saldoInicial() != null) c.setSaldoInicial(r.saldoInicial());
        if (r.ativa() != null) c.setAtiva(r.ativa());
        c = repo.save(c);
        return ResponseEntity.ok(
            new ContaResponse(c.getId(), c.getBanco(), c.getAgencia(), c.getConta(), c.getDigito(),
                c.getTipo(), c.getSaldoInicial(), c.getAtiva()));
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('financeiro:conta:escrita')")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) {
        ContaBancaria c = repo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, u.getEmpresaId())
            .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada"));
        c.setAtiva(false);
        repo.save(c);
        return ResponseEntity.noContent().build();
    }
}
