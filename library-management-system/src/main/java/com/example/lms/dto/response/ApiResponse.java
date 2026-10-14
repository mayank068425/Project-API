package com.example.lms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single envelope every endpoint returns.
 *
 * <p>Both success and failure use this shape, so a client parses one structure
 * rather than branching on the status code before it knows how to read the body.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, String errorCode,
                             String path, List<FieldError> fieldErrors, Instant timestamp) {

    public record FieldError(String field, String message, Object rejectedValue) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String path) {
        return new ApiResponse<>(false, message, null, errorCode, path, null, Instant.now());
    }

    public static <T> ApiResponse<T> validationError(String message, String path,
                                                      List<FieldError> fieldErrors) {
        return new ApiResponse<>(false, message, null, "VALIDATION_FAILED", path,
                fieldErrors, Instant.now());
    }
}
