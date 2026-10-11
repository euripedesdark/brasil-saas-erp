package br.com.brasil_saas.cadastro.controller;

import br.com.brasil_saas.cadastro.dto.CategoriaRequest;
import br.com.brasil_saas.cadastro.dto.CategoriaResponse;
import br.com.brasil_saas.cadastro.service.CategoriaService;
import br.com.brasil_saas.shared.tenant.EmpresaDaGravacao;
import br.com.brasil_saas.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cadastro/categorias")
@RequiredArgsConstructor
@Tag(name = "Cadastro - Categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    @PreAuthorize("hasAuthority('cadastro:categoria:escrita')")
    @Operation(summary = "Criar categoria")
    public ResponseEntity<ApiResponse<CategoriaResponse>> criar(@Valid @RequestBody CategoriaRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) {
        // A empresa vem do principal autenticado. Sem isso o INSERT fica com
        // empresa_id nulo (a coluna e' NOT NULL, via TenantEntity) e o banco
        // recusa com violacao de FK, devolvida como 409 — que parece
        // duplicidade mas e' falta do tenant.
        CategoriaResponse response = categoriaService.criar(request, EmpresaDaGravacao.de(usuarioAutenticado));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cadastro:categoria:escrita')")
    @Operation(summary = "Atualizar categoria")
    public ResponseEntity<ApiResponse<CategoriaResponse>> atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest request) {
        CategoriaResponse response = categoriaService.atualizar(id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cadastro:categoria:leitura')")
    @Operation(summary = "Buscar categoria por ID")
    public ResponseEntity<ApiResponse<CategoriaResponse>> buscarPorId(@PathVariable Long id) {
        CategoriaResponse response = categoriaService.buscarPorId(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('cadastro:categoria:leitura')")
    @Operation(summary = "Listar categorias")
    public ResponseEntity<ApiResponse<List<CategoriaResponse>>> listar() {
        List<CategoriaResponse> response = categoriaService.listar();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cadastro:categoria:escrita')")
    @Operation(summary = "Excluir categoria (soft delete)")
    public ResponseEntity<ApiResponse<Void>> excluir(@PathVariable Long id) {
        categoriaService.excluir(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
