package com.hms.exception;

/**
 * A request that is well-formed but not permitted by a domain rule.
 *
 * <p>Distinct from a validation failure: the payload is fine, the operation is
 * not. Deleting a department that still has doctors is the example that matters
 * here, and it maps to 409 rather than 400 because nothing about the request
 * itself is wrong.
 */
public class BusinessRuleException extends RuntimeException {

    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(message);
    }
}
