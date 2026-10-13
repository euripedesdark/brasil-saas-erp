package br.com.brasil_saas.shared.exception;

import br.com.brasil_saas.shared.model.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex, HttpServletRequest req) {
        log.warn("Regra de negócio: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), ex.getCode(), req);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(EntityNotFoundException ex, HttpServletRequest req) {
        log.warn("Não encontrado: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND", req);
    }

    /**
     * Excecoes com status proprio (ResponseStatusException), usadas por
     * modulos inteiros como contabilidade e compras. Sem este handler,
     * caiam no generico e voltavam 500: toda regra de negocio desses
     * modulos parecia erro do servidor, e a tela nao conseguia mostrar
     * a mensagem certa (ex.: periodo precisa estar fechado).
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> comStatusProprio(
            org.springframework.web.server.ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null || status.is5xxServerError()) status = HttpStatus.BAD_REQUEST;
        String mensagem = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        log.warn("Regra com status {}: {}", status.value(), mensagem);
        return build(status, mensagem, "ERRO_NEGOCIO", req);
    }

    /**
     * Recurso inexistente (ResourceNotFoundException do projeto).
     * Sem este handler, caia no generico e voltava 500.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> recursoNaoEncontrado(ResourceNotFoundException ex,
                                                                  HttpServletRequest req) {
        log.warn("Recurso inexistente: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), ex.getCode(), req);
    }

    /**
     * Corpo da requisicao ilegivel.
     *
     * Ocorre antes de qualquer validacao: mandar "saldo": "abc" num campo
     * BigDecimal faz o Jackson estourar ao desserializar, e sem este handler
     * virava 500. Para quem chamou, nao e erro do servidor — o pedido veio
     * errado, entao 400 e a resposta certa, com o campo Unified.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> corpoIlegivel(HttpMessageNotReadableException ex,
                                                        HttpServletRequest request) {
        String causa = ex.getMostSpecificCause().getMessage();
        String mensagem = "Corpo da requisicao invalido";
        if (causa != null && causa.contains("saldo")) mensagem = "Campo 'saldo' invalido";
        else if (causa != null && causa.contains("Could not resolve property")) {
            mensagem = "Campo desconhecido no corpo da requisicao";
        } else if (causa != null && causa.contains("not marked as ignorable")) {
            mensagem = "Corpo da requisicao malformado";
        }
        ApiResponse.ApiError erro = new ApiResponse.ApiError(null, mensagem, "REQUISICAO_INVALIDA");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(List.of(erro), request.getRequestURI(),
                        HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiResponse.ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toApiError)
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errors, req.getRequestURI(), 400));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.error("Integridade de dados: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Violação de integridade (duplicidade ou FK inválida)", "DATA_INTEGRITY", req);
    }

    /**
     * Negacao de acesso e resposta normal do sistema, nao erro interno.
     *
     * Sem este handler a excecao caia no catch-all e voltava 500, o que
     * distingueva "voce nao pode" de "o servidor quebrou" — e ainda
     * poluia o log com ERROR e stack trace a cada tentativa negada.
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> acessoNegado(AuthorizationDeniedException ex, HttpServletRequest req) {
        log.warn("Acesso negado em {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Voce nao tem permissao para esta operacao", "ACESSO_NEGADO", req);
    }

    /** Recurso inexistente: 404, nao 403 (nao revela que o recurso existe). */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> semElemento(NoSuchElementException ex, HttpServletRequest req) {
        log.warn("Recurso inexistente em {}", req.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Recurso nao encontrado", "NOT_FOUND", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> springNegado(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Acesso negado (Spring Security) em {}", req.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "Voce nao tem permissao para esta operacao", "ACESSO_NEGADO", req);
    }

    /**
     * Entrada invalida vinda do cliente e 400, nao 500.
     * IllegalArgumentException e lancada por servicos e controllers para
     * dados ruins; tratá-la como erro interno escondia o motivo da falha.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Entrada invalida em {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "DADOS_INVALIDOS", req);
    }

    /**
     * Excecoes do proprio Spring que hoje caem no catch-all e voltam 500.
     *
     * <p>Sao erros de <b>chamada do cliente</b> — parametro faltando, metodo
     * errado, recurso inexistente — e nao do servidor. Responder 500 por eles
     * mistura "o cliente errou a URL" com "o ERP quebrou", e ainda polui o log
     * com ERROR e stack trace a cada requisicao errada. E 24 dos 500 que
     * apareceram na varredura de endpoints eram exatamente isto.
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> requisicaoInvalida(Exception ex, HttpServletRequest req) {
        log.warn("Requisicao invalida em {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, mensagemLegivel(ex), "REQUISICAO_INVALIDA", req);
    }

    /** Metodo HTTP nao suportado: 405, com o Allow que o cliente precisa seguir. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> metodoNaoSuportado(HttpRequestMethodNotSupportedException ex,
                                                               HttpServletRequest req) {
        log.warn("Metodo {} nao suportado em {}", ex.getMethod(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(
                        List.of(ApiResponse.ApiError.builder()
                                .message("Metodo " + ex.getMethod() + " nao permitido em " + req.getRequestURI())
                                .code("METODO_NAO_PERMITIDO").build()),
                        req.getRequestURI(), 405));
    }

    /**
     * Recurso inexistente: 404.
     *
     * <p>Sem este handler o Spring devolve 500, o que faz o frontend mostrar
     * "erro interno" para o que e, na verdade, URL errada.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> recursoNaoEncontrado(NoResourceFoundException ex,
                                                                 HttpServletRequest req) {
        log.warn("Recurso inexistente em {}", req.getRequestURI());
        return build(HttpStatus.NOT_FOUND,
                "Recurso nao encontrado: " + req.getRequestURI(), "NOT_FOUND", req);
    }

    /**
     * Estado invalido do sistema impede a operacao: credencial nao configurada,
     * empresa nao definida, titulo ja baixado. Nao e entrada invalida (400) nem
     * queda do ERP, mas o status 500 e mantido para nao quebrar contrato com o
     * frontend.
     *
     * <p>A mensagem agora chega ao usuario. Antes os 59 lancamentos caavam no
     * catch-all e voltavam "Erro interno do servidor", escondido o motivo real
     * (por exemplo, a chave Stripe recusada vs. a Stripe fora do ar).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> estadoInvalido(IllegalStateException ex, HttpServletRequest req) {
        log.warn("Estado invalido em {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, mensagemLegivel(ex), "ESTADO_INVALIDO", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> generic(Exception ex, HttpServletRequest req) {
        log.error("Erro não tratado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", "INTERNAL_ERROR", req);
    }

    /**
     * A mensagem padrao do Spring vem com o nome do parametro e o tipo, o que
     * ajuda quem developing mas nao ajuda quem usa. Mantem o padrao quando nao
     * ha nada melhor a dizer.
     */
    private String mensagemLegivel(Exception ex) {
        String m = ex.getMessage();
        if (m == null || m.isBlank()) {
            return "Requisicao invalida";
        }
        int quebra = m.indexOf('\n');
        return quebra > 0 ? m.substring(0, quebra) : m;
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message, String code, HttpServletRequest req) {
        var error = ApiResponse.ApiError.builder().message(message).code(code).build();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(List.of(error), req.getRequestURI(), status.value()));
    }

    private ApiResponse.ApiError toApiError(FieldError fe) {
        return ApiResponse.ApiError.builder()
                .field(fe.getField())
                .message(fe.getDefaultMessage())
                .code("VALIDATION_ERROR")
                .build();
    }
}
