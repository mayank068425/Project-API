package com.hms.exception;

public class ResourceNotFoundException extends RuntimeException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience for the common "no X with id Y" case. */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("No " + resource + " found with id " + id);
    }
}
