package br.com.brasil_saas.fiscal.controller;

import br.com.brasil_saas.fiscal.model.Nfe;
import br.com.brasil_saas.fiscal.service.NFeService;
import br.com.brasil_saas.vendas.model.PedidoVenda;
import br.com.brasil_saas.vendas.repository.PedidoVendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/fiscal/nfe")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "brasil-saas.fiscal.sefaz.enabled", havingValue = "true")
public class NFeController {

    private final NFeService nfeService;
    private final PedidoVendaRepository pedidoVendaRepository;

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

    @PostMapping("/emitir/{pedidoId}")
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> emitir(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @PathVariable Long pedidoId) throws Exception {
        PedidoVenda pedido = pedidoVendaRepository.findByIdAndEmpresaId(pedidoId, empresaId)
                .orElseThrow(() -> new IllegalArgumentException("Pedido de venda nao encontrado"));
        String protocolo = nfeService.emitirNFe(empresaId, pedido);
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "protocolo", protocolo,
                "pedidoId", pedidoId
        ));
    }

    @PostMapping("/cancelar")
    @PreAuthorize("hasAnyRole('FINANCEIRO','GESTOR','GERENTE','DIRETORIA','ADMIN','SUPERUSER')")
    public ResponseEntity<Map<String, Object>> cancelar(
            @RequestHeader("X-Empresa-Id") Long empresaId,
            @RequestBody CancelamentoRequest request) throws Exception {
        String protocolo = nfeService.cancelarNFe(
                empresaId, request.chaveAcesso(), request.motivo());
        return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "protocolo", protocolo,
                "chaveAcesso", request.chaveAcesso()
        ));
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

    public record CancelamentoRequest(String chaveAcesso, String motivo) {}
}
