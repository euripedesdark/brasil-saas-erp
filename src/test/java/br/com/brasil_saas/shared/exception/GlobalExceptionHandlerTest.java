package br.com.brasil_saas.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    @Mock HttpServletRequest req;

    @Test
    void statusProprioPreservado() {
        when(req.getRequestURI()).thenReturn("/api/x");
        var r = handler.comStatusProprio(
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "periodo"), req);
        assertEquals(422, r.getStatusCode().value());
        assertEquals("periodo", r.getBody().getErrors().get(0).getMessage());
    }

    @Test
    void semMotivoUsaFraseDoStatus() {
        when(req.getRequestURI()).thenReturn("/api/x");
        var r = handler.comStatusProprio(new ResponseStatusException(HttpStatus.NOT_FOUND), req);
        assertEquals(404, r.getStatusCode().value());
    }

    @Test
    void erro5xxVira400() {
        when(req.getRequestURI()).thenReturn("/api/x");
        var r = handler.comStatusProprio(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "x"), req);
        assertEquals(400, r.getStatusCode().value());
    }
}
