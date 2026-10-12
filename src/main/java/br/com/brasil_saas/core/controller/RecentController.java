package br.com.brasil_saas.core.controller;

import br.com.brasil_saas.core.repository.AuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista as últimas alterações registradas na auditoria, para o painel de
 * "Resumo / edição rápida" das telas.
 */
@RestController
@RequestMapping("/api/core/recent")
@RequiredArgsConstructor
public class RecentController {

    private final AuditoriaRepository auditoriaRepository;

    @GetMapping("/updates")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> updates(@AuthenticationPrincipal AuthenticatedUser u,
                                             @RequestParam(defaultValue = "20") int limite) {
        int lim = Math.min(Math.max(limite, 1), 50);

        // A empresa vem do token. Antes era um @RequestParam opcional: sem ele a
        // consulta caia no ramo "sem empresa" e devolvia a auditoria de TODOS os
        // tenants — o usuario via quem mudou o que, e de onde, na empresa
        // vizinha. Sem empresa no token não há auditoria a listar.
        Long empresaId = u.getEmpresaId();
        if (empresaId == null) {
            return List.of();
        }

        return auditoriaRepository.findByEmpresaIdOrderByCreatedAtDesc(empresaId, PageRequest.of(0, lim))
                .stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("modulo", a.getTabela());
                    m.put("tipo", a.getOperacao());
                    m.put("idRegistro", a.getRegistroId());
                    m.put("data", a.getCreatedAt());
                    m.put("usuarioId", a.getUsuarioId());
                    m.put("ip", a.getIp());
                    return m;
                }).toList();
    }
}
