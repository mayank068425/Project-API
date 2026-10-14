package com.banking.entity;

import java.math.BigDecimal;

/**
 * Whether a ledger row adds to or subtracts from its account.
 *
 * <p>Stored separately from the type so a balance can be derived by summing
 * {@code signed(amount)} without a CASE over every transaction type. Adding a
 * new type then does not require revisiting every balance query.
 */
public enum TransactionDirection {

    CREDIT,
    DEBIT;

    public BigDecimal signed(BigDecimal amount) {
        return this == CREDIT ? amount : amount.negate();
    }
}
