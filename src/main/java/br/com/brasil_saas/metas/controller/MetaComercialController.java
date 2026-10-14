package br.com.brasil_saas.metas.controller;

import br.com.brasil_saas.metas.model.MetaComercial;
import br.com.brasil_saas.metas.service.MetaComercialService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendas/metas")
@RequiredArgsConstructor
public class MetaComercialController {
    private final MetaComercialService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MetaComercial> listar(@AuthenticationPrincipal AuthenticatedUser u,
                                      @RequestParam(required = false) Integer ano,
                                      @RequestParam(required = false) Integer mes) {
        return service.listar(u.getEmpresaId(), ano, mes);
    }

    @GetMapping("/resumo")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> resumo(@AuthenticationPrincipal AuthenticatedUser u,
                                      @RequestParam(required = false) Integer ano,
                                      @RequestParam(required = false) Integer mes) {
        return service.resumo(u.getEmpresaId(), ano, mes);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MetaComercial> criar(@RequestBody MetaComercial m, @AuthenticationPrincipal AuthenticatedUser u) {
        m.setId(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.salvar(u.getEmpresaId(), m));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MetaComercial atualizar(@PathVariable Long id, @RequestBody MetaComercial m, @AuthenticationPrincipal AuthenticatedUser u) {
        m.setId(id);
        return service.salvar(u.getEmpresaId(), m);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> excluir(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser u) {
        service.excluir(u.getEmpresaId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/realizado")
    @PreAuthorize("isAuthenticated()")
    public MetaComercial realizado(@PathVariable Long id, @RequestParam BigDecimal valor, @AuthenticationPrincipal AuthenticatedUser u) {
        return service.registrarRealizado(u.getEmpresaId(), id, valor);
    }
}
