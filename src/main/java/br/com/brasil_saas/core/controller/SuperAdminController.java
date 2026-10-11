package br.com.brasil_saas.core.controller;

import br.com.brasil_saas.core.repository.PerfilRepository;
import br.com.brasil_saas.core.repository.UsuarioRepository;
import br.com.brasil_saas.core.service.PermissionService;
import br.com.brasil_saas.shared.image.ImagemDocumento;
import br.com.brasil_saas.shared.service.GenericoImagemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final GenericoImagemService imagemService;
    private final PermissionService permissionService;

    @PostMapping("/assets/background")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> uploadBackground(@RequestParam("file") MultipartFile file) throws IOException {
        imagemService.salvarImagemSistema("SISTEMA_BACKGROUND", file);
        return ResponseEntity.ok("Background atualizado com sucesso");
    }

    @PostMapping("/assets/login")
    @PreAuthorize("hasAnyRole('SUPERUSER', 'SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> uploadLogin(@RequestParam("file") MultipartFile file) throws IOException {
        imagemService.salvarImagemSistema("SISTEMA_LOGIN", file);
        return ResponseEntity.ok("Tela de login atualizada com sucesso");
    }

    @GetMapping("/assets/system/{tipo}")
    public ResponseEntity<byte[]> getSystemAsset(@PathVariable String tipo) {
        ImagemDocumento imagem = imagemService.buscarImagemSistema(tipo);

        if (imagem == null || imagem.getConteudo() == null || imagem.getConteudo().length == 0) {
            return ResponseEntity.noContent().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String contentType = imagem.getContentType();

        if (contentType != null && !contentType.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (IllegalArgumentException ignored) {
                // Documento legado pode não possuir um MIME válido.
                // Nesse caso, entrega o conteúdo sem bloquear o login.
            }
        } else {
            String nomeArquivo = imagem.getNomeArquivo();
            if (nomeArquivo != null) {
                String nome = nomeArquivo.toLowerCase();
                if (nome.endsWith(".png")) {
                    mediaType = MediaType.IMAGE_PNG;
                } else if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) {
                    mediaType = MediaType.IMAGE_JPEG;
                } else if (nome.endsWith(".gif")) {
                    mediaType = MediaType.IMAGE_GIF;
                } else if (nome.endsWith(".webp")) {
                    mediaType = MediaType.parseMediaType("image/webp");
                }
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(imagem.getConteudo().length)
                .body(imagem.getConteudo());
    }

    /*
     * A atribuicao de perfil mora em UsuarioAdminController
     * (/api/superadmin/usuarios/permissao). Aqui ficava uma copia, e as duas
     * rotas colidiam — o contexto nem subia ("Ambiguous mapping").
     * Mantida apenas a que tem os dois eixos: perfil + modulos.
     */
}
