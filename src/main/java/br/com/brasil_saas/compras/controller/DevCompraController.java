package br.com.brasil_saas.compras.controller;
import br.com.brasil_saas.compras.model.DevCompra;
import br.com.brasil_saas.compras.model.DevCompraItem;
import br.com.brasil_saas.compras.service.DevCompraService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/compras/devolucoes") @RequiredArgsConstructor
public class DevCompraController {
    private final DevCompraService svc;
    @GetMapping @PreAuthorize("hasAuthority('compras:devolucao:leitura')")
    public List<DevCompra> listar(@AuthenticationPrincipal AuthenticatedUser u) { return svc.listar(u.getEmpresaId()); }
    @GetMapping("/{id}/itens") @PreAuthorize("hasAuthority('compras:devolucao:leitura')")
    public List<DevCompraItem> itens(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { return svc.itensDe(u.getEmpresaId(), id); }
    @PostMapping @PreAuthorize("hasAuthority('compras:devolucao:escrita')")
    @SuppressWarnings("unchecked")
    public DevCompra solicitar(@AuthenticationPrincipal AuthenticatedUser u, @RequestBody Map<String, Object> corpo) { Long pedidoId = Long.valueOf(String.valueOf(corpo.get("pedidoId"))); String motivo = (String) corpo.get("motivo"); List<Map<String, Object>> itensReq = (List<Map<String, Object>>) (List<?>) corpo.get("itens"); return svc.solicitar(u.getEmpresaId(), pedidoId, motivo, itensReq); }
    @PostMapping("/{id}/devolver") @PreAuthorize("hasAuthority('compras:devolucao:escrita')")
    public DevCompra devolver(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { return svc.devolver(u.getEmpresaId(), id); }
    @PostMapping("/{id}/cancelar") @PreAuthorize("hasAuthority('compras:devolucao:escrita')")
    public DevCompra cancelar(@AuthenticationPrincipal AuthenticatedUser u, @PathVariable Long id) { return svc.cancelar(u.getEmpresaId(), id); }
}
