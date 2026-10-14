package com.example.lms.exception;

import com.example.lms.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
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

import java.util.List;

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

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> businessRule(BusinessRuleException ex,
                                                          HttpServletRequest request) {
        // 422, not 400: nothing about the request is malformed, the operation is
        // simply not permitted in the current state.
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(
                BusinessRuleException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BookNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> notAvailable(BookNotAvailableException ex,
                                                           HttpServletRequest request) {
        // The reason is appended so the desk knows whether to offer a
        // reservation: no point offering one when the copies are already held for
        // people ahead of this member in the queue.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                BookNotAvailableException.CODE,
                ex.getMessage() + " [" + ex.getReason() + "]", request.getRequestURI()));
    }

    @ExceptionHandler(MemberLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> limitExceeded(MemberLimitExceededException ex,
                                                            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                MemberLimitExceededException.CODE, ex.getMessage(), request.getRequestURI()));
    }

    /**
     * A unique constraint fired.
     *
     * <p>Most often a duplicate ISBN, or a return processed twice hitting the
     * unique index on {@code book_return.issue_id}. Both are the constraint doing
     * its job, so 409 rather than 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        // The driver's message names the constraint and often the offending
        // value. Useful in the log, and it has no business reaching a caller.
        log.warn("constraint violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "CONSTRAINT_VIOLATION", "This request conflicts with an existing record",
                request.getRequestURI()));
    }

    /**
     * Lock contention on a popular title.
     *
     * <p>429 rather than 500: the request was valid, another issue simply held
     * the row, and retrying is the correct client behaviour.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class,
            OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> locking(Exception ex,
                                                      HttpServletRequest request) {
        log.warn("lock contention on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(
                "CONCURRENT_MODIFICATION",
                "That title is busy with another request. Try again.",
                request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> badCredentials(BadCredentialsException ex,
                                                             HttpServletRequest request) {
        // Deliberately the exception's own message, which is identical for an
        // unknown user, a wrong password and a locked account.
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
        // Every failing field at once. Returning only the first turns a form with
        // three mistakes into three round trips.
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
     * <p>Logs the stack trace and returns a generic message. An unexpected
     * exception's text frequently contains a SQL fragment, a file path or a class
     * name, and none of that belongs in an HTTP response.
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
