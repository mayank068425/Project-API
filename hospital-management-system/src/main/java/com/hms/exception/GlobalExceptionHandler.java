package com.hms.exception;

import com.hms.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex,
                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ResourceNotFoundException.CODE, ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateResourceException ex,
                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(DuplicateResourceException.CODE, ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> businessRule(BusinessRuleException ex,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(BusinessRuleException.CODE, ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * A unique constraint fired at the database.
     *
     * <p>The service checks for duplicates first so the caller gets a readable
     * message, but that check loses the race against a simultaneous insert of the
     * same value. The constraint is the actual guarantee and this handler is what
     * turns it into a 409 instead of a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex,
                                              HttpServletRequest request) {
        // The driver's message names the constraint and often the offending
        // value. That is useful in the log and has no business reaching a caller.
        log.warn("constraint violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("CONSTRAINT_VIOLATION",
                        "This request conflicts with an existing record",
                        request.getRequestURI()));
    }

    /** Bean Validation on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex,
                                               HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage(),
                        f.getRejectedValue()))
                .toList();
        // Every failing field at once. Returning only the first turns a form with
        // three mistakes into three round trips.
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), Instant.now(),
                violations));
    }

    /** Bean Validation on a path variable or request parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex,
                                                        HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(),
                        v.getMessage(), v.getInvalidValue()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED",
                "Request validation failed", request.getRequestURI(), Instant.now(),
                violations));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex,
                                                 HttpServletRequest request) {
        String expected = ex.getRequiredType() == null ? "the expected type"
                : ex.getRequiredType().getSimpleName();
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_PARAMETER",
                "'" + ex.getName() + "' must be " + expected, request.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex,
                                                     HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex,
                                               HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of("MALFORMED_REQUEST",
                "Request body could not be parsed", request.getRequestURI()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> noHandler(NoHandlerFoundException ex,
                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND",
                "No endpoint " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException ex,
                                                    HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("INVALID_ARGUMENT", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * The catch-all.
     *
     * <p>Logs the full stack trace and returns a generic message. An unexpected
     * exception's text frequently contains a SQL fragment, a file path or a class
     * name, and none of that belongs in an HTTP response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled exception on {} {}", request.getMethod(),
                request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred",
                        request.getRequestURI()));
    }
}
