package com.banking.exception;

import java.math.BigDecimal;

/**
 * The account cannot fund this debit.
 *
 * <p>Carries the shortfall and the reason. "Insufficient balance" covers two
 * different situations and the customer needs to be told which: the balance is
 * genuinely too low, or the balance is fine but the withdrawal would breach the
 * account's minimum balance requirement. The second is fixable by withdrawing
 * less; the first is not.
 *
 * <p>The available figure is included but the raw ledger balance is not, because
 * the amount a customer can actually spend is the one they can act on.
 */
public class InsufficientBalanceException extends RuntimeException {

    public static final String CODE = "INSUFFICIENT_BALANCE";

    public enum Reason {
        BALANCE_TOO_LOW,
        WOULD_BREACH_MINIMUM_BALANCE,
        EXCEEDS_DAILY_LIMIT
    }

    private final BigDecimal available;
    private final BigDecimal requested;
    private final Reason reason;

    public InsufficientBalanceException(String message, BigDecimal available,
                                        BigDecimal requested, Reason reason) {
        super(message);
        this.available = available;
        this.requested = requested;
        this.reason = reason;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getShortfall() {
        return requested.subtract(available).max(BigDecimal.ZERO);
    }

    public Reason getReason() {
        return reason;
    }
}
