package com.supportai.exception;

/**
 * A well-formed request that a domain rule does not permit.
 *
 * <p>An illegal incident status transition, superseding a document with itself.
 * Nothing about the payload is malformed, so 422 rather than 400.
 */
public class BusinessRuleException extends RuntimeException {

    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(message);
    }
}
