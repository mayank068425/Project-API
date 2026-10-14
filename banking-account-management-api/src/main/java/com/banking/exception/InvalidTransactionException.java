package com.banking.exception;

/**
 * A transaction that is well-formed but not permitted.
 *
 * <p>Distinct from a validation failure: the payload parses and the amounts are
 * positive, but the operation itself is refused. A transfer to the same account
 * on both sides, a debit against a frozen account, or a payment to a beneficiary
 * still inside its cooling-off window.
 */
public class InvalidTransactionException extends RuntimeException {

    public static final String CODE = "INVALID_TRANSACTION";

    public enum Reason {
        SAME_ACCOUNT,
        SOURCE_NOT_ACTIVE,
        DESTINATION_NOT_ACTIVE,
        BENEFICIARY_NOT_VERIFIED,
        BENEFICIARY_IN_COOLING_PERIOD,
        AMOUNT_NOT_POSITIVE,
        ACCOUNT_NOT_OWNED
    }

    private final Reason reason;

    public InvalidTransactionException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
