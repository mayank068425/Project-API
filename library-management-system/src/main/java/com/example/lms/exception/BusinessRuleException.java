package com.example.lms.exception;

/**
 * A well-formed request that a domain rule does not permit.
 *
 * <p>Distinct from a validation failure: the payload is fine, the operation is
 * not. Returning a book that is already returned is the example — nothing about
 * the request is malformed.
 */
public class BusinessRuleException extends RuntimeException {

    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(message);
    }
}
