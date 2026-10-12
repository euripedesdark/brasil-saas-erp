package br.com.brasil_saas.rh.controller;

import br.com.brasil_saas.rh.model.Funcionario;
import br.com.brasil_saas.rh.repository.FuncionarioRepository;
import br.com.brasil_saas.shared.dto.ImagemDto;
import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.security.AuthenticatedUser;
import br.com.brasil_saas.shared.service.GenericoImagemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Tag(name = "RH - Foto do Funcionário")
@RestController
// Base em /api/rh/funcionarios e a foto como sub-recurso /{id}/foto, igual a
// imagem do produto (/{id}/imagens). Antes o id vinha na query (?id=) e a URL
// gravada na coluna fotoUrl — "/api/rh/funcionarios/{id}/foto" — nao existia em
// rota nenhuma: quem tentasse usar aquele valor recebia 404.
@RequestMapping("/api/rh/funcionarios")
@RequiredArgsConstructor
public class FuncionarioFotoController {

    private final FuncionarioRepository funcionarioRepository;
    private final GenericoImagemService imagemService;

    @PostMapping(value = "/{id}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    @Operation(summary = "Atualizar foto do funcionário")
    public ResponseEntity<ImagemDto> atualizarFoto(
            @PathVariable Long id,
            @RequestParam("foto") MultipartFile arquivo,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) throws IOException {

        Optional<Funcionario> optionalFuncionario = funcionarioRepository.findById(id);
        if (optionalFuncionario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Funcionario funcionario = optionalFuncionario.get();
        
        // Verificar se o funcionário pertence à mesma empresa do usuário autenticado
        if (!funcionario.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Salvar imagem no MongoDB
        ImagemDocumento imagemDoc = imagemService.salvarImagem(
            usuarioAutenticado.getEmpresaId(), 
            "funcionario_foto", 
            funcionario.getId(), 
            arquivo
        );
        
        // Atualizar dados da imagem no funcionário
        funcionario.setFotoUrl("/api/rh/funcionarios/" + funcionario.getId() + "/foto"); // Caminho virtual para recuperação
        funcionario.setFotoTipoConteudo(imagemDoc.getContentType());
        funcionario.setFotoTamanho(imagemDoc.getTamanho());
        
        // Salvar funcionário
        funcionario = funcionarioRepository.save(funcionario);
        
        ImagemDto imagemDto = new ImagemDto();
        imagemDto.setUrl(funcionario.getFotoUrl());
        imagemDto.setTipoConteudo(funcionario.getFotoTipoConteudo());
        imagemDto.setTamanho(funcionario.getFotoTamanho());
        
        return ResponseEntity.ok(imagemDto);
    }

    @GetMapping("/{id}/foto")
    @PreAuthorize("hasAuthority('rh:funcionario:leitura')")
    @Operation(summary = "Obter foto do funcionário")
    public ResponseEntity<byte[]> obterFoto(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) {
        
        Optional<Funcionario> optionalFuncionario = funcionarioRepository.findById(id);
        if (optionalFuncionario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Funcionario funcionario = optionalFuncionario.get();
        
        // Verificar se o funcionário pertence à mesma empresa do usuário autenticado
        if (!funcionario.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ImagemDocumento imagemDoc = imagemService.buscarImagem(usuarioAutenticado.getEmpresaId(), "funcionario_foto", id);
        if (imagemDoc == null || imagemDoc.getConteudo() == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(imagemDoc.getContentType()));
        headers.setContentLength(imagemDoc.getConteudo().length);
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(imagemDoc.getConteudo());
    }

    @DeleteMapping("/{id}/foto")
    @PreAuthorize("hasAuthority('rh:funcionario:escrita')")
    @Operation(summary = "Remover foto do funcionário")
    public ResponseEntity<Void> removerFoto(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) {

        Optional<Funcionario> optionalFuncionario = funcionarioRepository.findById(id);
        if (optionalFuncionario.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Funcionario funcionario = optionalFuncionario.get();
        
        // Verificar se o funcionário pertence à mesma empresa do usuário autenticado
        if (!funcionario.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Remover imagem do MongoDB
        imagemService.removerImagem(usuarioAutenticado.getEmpresaId(), "funcionario_foto", funcionario.getId());
        
        funcionario.setFotoUrl(null);
        funcionario.setFotoTipoConteudo(null);
        funcionario.setFotoTamanho(null);
        
        funcionarioRepository.save(funcionario);
        
        return ResponseEntity.noContent().build();
    }
}