package com.supportai.exception;

import com.supportai.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ResourceNotFoundException ex,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                ResourceNotFoundException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate(DuplicateResourceException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                DuplicateResourceException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * The assistant declined, or could not run.
     *
     * <p>The status code carries the distinction that matters most in this API.
     * {@code INSUFFICIENT_CONTEXT} and {@code OUTPUT_REJECTED} are 422 — the
     * system worked correctly and refused to ground an answer it could not
     * support. {@code PROVIDER_UNAVAILABLE} is 503, and only that one is worth
     * retrying.
     *
     * <p>Collapsing them into one code would have clients retrying a refusal they
     * should be showing to the user, forever.
     */
    @ExceptionHandler(AssistantException.class)
    public ResponseEntity<ApiResponse<Object>> assistant(AssistantException ex,
                                                          HttpServletRequest request) {
        HttpStatus status = ex.isRetryable()
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNPROCESSABLE_ENTITY;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("kind", ex.getKind().name());
        detail.put("retryable", ex.isRetryable());

        if (ex.getKind() == AssistantException.Kind.OUTPUT_REJECTED) {
            detail.put("note", "An answer was generated and failed verification. It has been "
                    + "withheld rather than shown with a warning: a caveat above a subtly "
                    + "wrong command does not stop anyone running it.");
        }

        log.info("assistant declined on {}: {} - {}", request.getRequestURI(),
                ex.getKind(), ex.getMessage());

        return ResponseEntity.status(status).body(new ApiResponse<>(false, ex.getMessage(),
                detail, AssistantException.CODE, request.getRequestURI(), null, Instant.now()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(
                BusinessRuleException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("constraint violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "CONSTRAINT_VIOLATION", "This request conflicts with an existing record",
                request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> badCredentials(BadCredentialsException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                "INVALID_CREDENTIALS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                "ACCESS_DENIED", "Insufficient privileges", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        List<ApiResponse.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.FieldError(error.getField(),
                        error.getDefaultMessage(), error.getRejectedValue()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validationError(
                "Request validation failed", request.getRequestURI(), fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraintViolation(ConstraintViolationException ex,
                                                                  HttpServletRequest request) {
        List<ApiResponse.FieldError> fields = ex.getConstraintViolations().stream()
                .map(violation -> new ApiResponse.FieldError(
                        violation.getPropertyPath().toString(), violation.getMessage(),
                        violation.getInvalidValue()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validationError(
                "Request validation failed", request.getRequestURI(), fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest request) {
        String expected = ex.getRequiredType() == null
                ? "the expected type" : ex.getRequiredType().getSimpleName();
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_PARAMETER",
                "'" + ex.getName() + "' must be " + expected, request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> missingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("MALFORMED_REQUEST",
                "Request body could not be parsed", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException ex,
                                                              HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_ARGUMENT",
                ex.getMessage(), request.getRequestURI()));
    }

    /**
     * The catch-all.
     *
     * <p>Logs the stack trace, returns a generic message. An unexpected
     * exception's text frequently carries a SQL fragment or a row value, and here
     * that value may be a customer identifier out of an incident description.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex,
                                                         HttpServletRequest request) {
        log.error("unhandled exception on {} {}", request.getMethod(),
                request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                "INTERNAL_ERROR", "An unexpected error occurred", request.getRequestURI()));
    }
}
