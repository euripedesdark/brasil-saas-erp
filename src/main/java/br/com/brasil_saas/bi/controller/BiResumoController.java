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

    /**
     * Resumo do BI.
     *
     * <p>Sem {@code inicio} e {@code fim}, responde o total do banco de BI na
     * maior janela que o servico aceita (366 dias). E' o que a tela mostra: o
     * painel do Grafana le a view inteira, e um total aqui e um total la.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<BiResumo> consultar(@AuthenticationPrincipal AuthenticatedUser usuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        LocalDate[] janela = janela(inicio, fim);
        return ApiResponse.success(service.consultar(usuario.getEmpresaId(), janela[0], janela[1]));
    }

    @GetMapping("/{area}")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN', 'DIRETORIA', 'GERENTE', 'GESTOR')")
    public ApiResponse<BiResumo> consultarArea(@AuthenticationPrincipal AuthenticatedUser usuario,
            @PathVariable String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        LocalDate[] janela = janela(inicio, fim);
        return ApiResponse.success(service.consultarArea(usuario.getEmpresaId(), janela[0], janela[1], area));
    }

    /** A janela pedida; sem datas, a maior que o servico aceita. */
    private LocalDate[] janela(LocalDate inicio, LocalDate fim) {
        if (inicio != null && fim != null) {
            return new LocalDate[] { inicio, fim };
        }
        LocalDate hoje = LocalDate.now();
        return new LocalDate[] { hoje.minusDays(365), hoje };
    }
}
