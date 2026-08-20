package com.tomas.cuaderno.common.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex, HttpServletRequest request) { return problem(HttpStatus.NOT_FOUND, ex, request); }
    @ExceptionHandler({BadRequestException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception ex, HttpServletRequest request) {
        String detail = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.joining(", "))
                : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, detail, request);
    }
    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException ex, HttpServletRequest request) { return problem(HttpStatus.FORBIDDEN, ex, request); }
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized(AuthenticationException ex, HttpServletRequest request) { return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", request); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException ex, HttpServletRequest request) { return problem(HttpStatus.CONFLICT, "The requested change conflicts with existing data", request); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) { return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported request content type", request); }
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error at {}", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ProblemDetail problem(HttpStatus status, Exception ex, HttpServletRequest request) { return problem(status, ex.getMessage(), request); }
    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail == null ? status.getReasonPhrase() : detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://cuaderno.local/problems/" + status.value()));
        pd.setProperty("path", request.getRequestURI());
        return pd;
    }
}
