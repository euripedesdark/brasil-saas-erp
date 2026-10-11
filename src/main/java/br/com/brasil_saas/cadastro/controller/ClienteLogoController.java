package br.com.brasil_saas.cadastro.controller;

import br.com.brasil_saas.cadastro.model.Cliente;
import br.com.brasil_saas.cadastro.repository.ClienteRepository;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Tag(name = "Cadastro - Logo do Cliente")
@RestController
@RequestMapping("/api/cadastro/clientes/logo")
@RequiredArgsConstructor
public class ClienteLogoController {

    private final ClienteRepository clienteRepository;
    private final GenericoImagemService imagemService;

    @PostMapping
    @PreAuthorize("hasAuthority('cadastro:cliente:escrita')")
    @Operation(summary = "Atualizar logo do cliente")
    public ResponseEntity<ImagemDto> atualizarLogo(
            @RequestParam("id") Long clienteId,
            @RequestParam("logo") MultipartFile arquivo,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) throws IOException {
        
        Optional<Cliente> optionalCliente = clienteRepository.findById(clienteId);
        if (optionalCliente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Cliente cliente = optionalCliente.get();
        
        // Verificar se o cliente pertence à mesma empresa do usuário autenticado
        if (!cliente.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Salvar imagem no MongoDB
        ImagemDocumento imagemDoc = imagemService.salvarImagem(
            usuarioAutenticado.getEmpresaId(), 
            "cliente_logo", 
            cliente.getId(), 
            arquivo
        );
        
        // Atualizar dados do logo no cliente
        cliente.setLogoUrl("/api/cadastro/clientes/" + cliente.getId() + "/logo"); // Caminho virtual para recuperação
        cliente.setLogoTipoConteudo(imagemDoc.getContentType());
        cliente.setLogoTamanho(imagemDoc.getTamanho());
        
        // Salvar cliente
        cliente = clienteRepository.save(cliente);
        
        ImagemDto imagemDto = new ImagemDto();
        imagemDto.setUrl(cliente.getLogoUrl());
        imagemDto.setTipoConteudo(cliente.getLogoTipoConteudo());
        imagemDto.setTamanho(cliente.getLogoTamanho());
        
        return ResponseEntity.ok(imagemDto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('cadastro:cliente:leitura')")
    @Operation(summary = "Obter logo do cliente")
    public ResponseEntity<byte[]> obterLogo(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) {
        
        Optional<Cliente> optionalCliente = clienteRepository.findById(id);
        if (optionalCliente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Cliente cliente = optionalCliente.get();
        
        // Verificar se o cliente pertence à mesma empresa do usuário autenticado
        if (!cliente.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        ImagemDocumento imagemDoc = imagemService.buscarImagem(usuarioAutenticado.getEmpresaId(), "cliente_logo", id);
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

    @DeleteMapping
    @PreAuthorize("hasAuthority('cadastro:cliente:escrita')")
    @Operation(summary = "Remover logo do cliente")
    public ResponseEntity<Void> removerLogo(
            @RequestParam("id") Long clienteId,
            @AuthenticationPrincipal AuthenticatedUser usuarioAutenticado) {
        
        Optional<Cliente> optionalCliente = clienteRepository.findById(clienteId);
        if (optionalCliente.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Cliente cliente = optionalCliente.get();
        
        // Verificar se o cliente pertence à mesma empresa do usuário autenticado
        if (!cliente.getEmpresaId().equals(usuarioAutenticado.getEmpresaId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Remover imagem do MongoDB
        imagemService.removerImagem(usuarioAutenticado.getEmpresaId(), "cliente_logo", cliente.getId());
        
        cliente.setLogoUrl(null);
        cliente.setLogoTipoConteudo(null);
        cliente.setLogoTamanho(null);
        
        clienteRepository.save(cliente);
        
        return ResponseEntity.noContent().build();
    }
}