package com.hms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String path, Instant timestamp,
                       List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message, Object rejectedValue) {
    }

    public static ApiError of(String code, String message, String path) {
        return new ApiError(code, message, path, Instant.now(), null);
    }
}
