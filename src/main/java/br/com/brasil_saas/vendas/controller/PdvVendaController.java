package br.com.brasil_saas.vendas.controller;

import br.com.brasil_saas.vendas.service.PedidoVendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.util.Map;

@RestController
@RequestMapping("/api/vendas/pdv")
@RequiredArgsConstructor
public class PdvVendaController {

    private final PedidoVendaService service;

    @PostMapping("/venda-rapida")
    @PreAuthorize("hasAuthority('vendas:pedido:escrita')")
    public Map<String, Object> vendaRapida(@AuthenticationPrincipal AuthenticatedUser u,
            @RequestBody PedidoVendaService.VendaRapidaRequest request) {
        return service.vendaRapida(u.getEmpresaId(), request);
    }
}
