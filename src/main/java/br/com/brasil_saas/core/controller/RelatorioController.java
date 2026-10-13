package br.com.brasil_saas.core.controller;

import br.com.brasil_saas.core.model.RelatorioResponse;
import br.com.brasil_saas.core.service.RelatorioService;
import br.com.brasil_saas.core.service.report.PdfGeneratorService;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController("coreRelatorioController")
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final PdfGeneratorService pdfGeneratorService;

    public record MemorandoRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 50) String numero,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String assunto,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 500) String destinatario,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20000) String texto) {}

    @PostMapping("/memorando/pdf")
    public ResponseEntity<byte[]> memorandoPdf(@AuthenticationPrincipal AuthenticatedUser user,
            @jakarta.validation.Valid @RequestBody MemorandoRequest request) {
        Long empresaId = resolverEmpresa(user, Map.of());
        byte[] pdf = br.com.brasil_saas.core.service.report.JasperReportRenderer.shared().memorando(
                request.numero(), request.assunto(), "Empresa " + empresaId, user.getUsername(),
                request.destinatario(), request.texto());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("memorando.pdf").build().toString()).body(pdf);
    }

    @GetMapping("/{tipo}")
    public ResponseEntity<RelatorioResponse> getRelatorio(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String tipo,
            @RequestParam Map<String, Object> filtros) {

        // O tenant vem sempre do token; aceitar empresaId do cliente permitiria
        // ler o relatorio de outra empresa.
        Long empresaId = resolverEmpresa(user, filtros);

        try {
            RelatorioResponse report = relatorioService.gerarRelatorio(tipo, empresaId, filtros);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Gera o relatório gerencial em PDF. O frontend (Relatorios.jsx) chama
     * /api/relatorios/pdf/{tipo} via window.open, entao o prefixo "pdf" precisa
     * ser declarado antes do catch-all /{tipo}.
     */
    @GetMapping("/pdf/{tipo}")
    public ResponseEntity<byte[]> getRelatorioPdf(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String tipo,
            @RequestParam Map<String, Object> filtros) {

        Long empresaId = resolverEmpresa(user, filtros);

        RelatorioResponse report;
        try {
            report = relatorioService.gerarRelatorio(tipo, empresaId, filtros);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        List<Map<String, Object>> dados = report.getDados() == null ? List.of() : report.getDados();
        List<String> columns = dados.isEmpty() ? List.of() : new ArrayList<>(dados.get(0).keySet());

        try {
            byte[] pdf = "FISCAL".equalsIgnoreCase(tipo) ? pdfGeneratorService.generateFiscalReport(
                    report.getNomeRelatorio(), report.getPeriodo(), empresaId, "", java.time.LocalDate.now(),
                    dados, columns, report.getResumo()) :
                    br.com.brasil_saas.core.service.report.JasperReportRenderer.shared().pdfPorTipo(
                            tipo, report.getNomeRelatorio(), report.getPeriodo(), "Empresa " + empresaId,
                            dados, report.getResumo());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("relatorio-" + tipo.toLowerCase() + ".pdf", StandardCharsets.UTF_8)
                    .build());
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Long resolverEmpresa(AuthenticatedUser user, Map<String, Object> filtros) {
        // Para download via window.open o token vai na querystring, nao no header.
        if (user != null && user.getEmpresaId() != null) {
            return user.getEmpresaId();
        }
        throw new org.springframework.web.server.ResponseStatusException(
                user == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                "Usuario autenticado com empresa e obrigatorio");
    }
}
