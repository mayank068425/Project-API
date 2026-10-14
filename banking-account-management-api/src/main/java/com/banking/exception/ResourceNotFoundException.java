package com.banking.exception;

public class ResourceNotFoundException extends RuntimeException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("No " + resource + " found with id " + id);
    }
}
