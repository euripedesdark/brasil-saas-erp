package br.com.brasil_saas.fiscal.controller;

import br.com.brasil_saas.fiscal.service.NFeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fiscal/nfe")
@RequiredArgsConstructor
public class NFeController {

    private final NFeService nfeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<Page<NfeResumo>> listar(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageRequest pageable = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "dataEmissao"));
        Page<NfeResumo> resposta = nfeService.listar(empresaId, pageable).map(n -> new NfeResumo(
                n.getId(), n.getNumero(), n.getSerie(), n.getStatus(), n.getChaveAcesso(),
                n.getValorTotal(), n.getDataEmissao(), n.getXml() != null && !n.getXml().isBlank()));
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/xml/{id}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<String> xml(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @PathVariable Long id) {
        var nota = nfeService.consultarPersistidaPorId(empresaId, id);
        if (nota.getXml() == null || nota.getXml().isBlank()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(nota.getXml());
    }

    @GetMapping("/documento/{chaveAcesso}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<br.com.brasil_saas.fiscal.model.Nfe> documento(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @PathVariable String chaveAcesso) {
        return ResponseEntity.ok(nfeService.consultarPersistida(empresaId, chaveAcesso));
    }

    @GetMapping(value = "/consultar/{chaveAcesso}", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<String> consultar(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @PathVariable String chaveAcesso) throws Exception {
        return ResponseEntity.ok(nfeService.consultarSituacao(empresaId, chaveAcesso));
    }

    public record NfeResumo(
            Long id,
            Long numero,
            String serie,
            String status,
            String chaveAcesso,
            java.math.BigDecimal valorTotal,
            java.time.LocalDateTime dataEmissao,
            boolean xml
    ) {}

}
