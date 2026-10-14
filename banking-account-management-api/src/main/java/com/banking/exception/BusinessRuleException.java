package com.banking.exception;

/**
 * A well-formed request that a domain rule does not permit, outside the
 * transaction path.
 *
 * <p>Closing an account that still holds money, deleting a customer with open
 * accounts. Nothing about the request is malformed, so 422 rather than 400.
 */
public class BusinessRuleException extends RuntimeException {

    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String message) {
        super(message);
    }
}
