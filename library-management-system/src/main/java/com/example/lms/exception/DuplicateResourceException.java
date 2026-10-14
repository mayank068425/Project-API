package com.example.lms.exception;

public class DuplicateResourceException extends RuntimeException {

    public static final String CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException of(String resource, Object id) {
        return new DuplicateResourceException("No " + resource + " found with id " + id);
    }
}
