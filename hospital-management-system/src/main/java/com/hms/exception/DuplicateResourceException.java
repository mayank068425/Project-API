package com.hms.exception;

public class DuplicateResourceException extends RuntimeException {

    public static final String CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(message);
    }
}
