package br.com.brasil_saas.bi.controller;

import br.com.brasil_saas.bi.dto.BiResumo;
import br.com.brasil_saas.bi.service.BiResumoService;
import br.com.brasil_saas.shared.dto.ApiResponse;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bi/resumo")
public class BiResumoController {
    private final BiResumoService service;
    public BiResumoController(BiResumoService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<BiResumo> consultar(@AuthenticationPrincipal AuthenticatedUser usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ApiResponse.success(service.consultar(usuario.getEmpresaId(), inicio, fim));
    }

    @GetMapping("/{area}")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<BiResumo> consultarArea(@AuthenticationPrincipal AuthenticatedUser usuario,
            @PathVariable String area,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ApiResponse.success(service.consultarArea(usuario.getEmpresaId(), inicio, fim, area));
    }
}
