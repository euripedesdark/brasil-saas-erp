package br.com.brasil_saas.fiscal.sp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Traduz excecao em resposta HTTP.
 *
 * <p>A prefeitura recusando a nota e <b>422</b>, nao 500: a mensagem esta
 * correta, quem precisa corrigir e quem emitiu, e a resposta traz o codigo da
 * prefeitura (306, 307, 640, 1206, 641...) para o usuario agir.
 */
@Slf4j
@RestControllerAdvice
public class NfseSpExceptionHandler {

    @ExceptionHandler(NfseSpException.class)
    public ResponseEntity<?> nfse(NfseSpException e) {
        log.warn("NFS-e recusada ou nao executada: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of("sucesso", false, "erro", e.getMessage()));
    }

    /** Falha de rede ou certificado ilegivel: e do servidor, nao da nota. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> generico(Exception e) {
        log.error("Falha inesperada na integracao NFS-e SP", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("sucesso", false,
                        "erro", "Falha inesperada: " + e.getMessage()));
    }
}
