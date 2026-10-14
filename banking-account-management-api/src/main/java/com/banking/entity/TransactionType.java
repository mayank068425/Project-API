package com.banking.entity;

public enum TransactionType {

    DEPOSIT,
    WITHDRAWAL,
    /** The debit half of a transfer. */
    TRANSFER_OUT,
    /** The credit half of a transfer, sharing the same reference. */
    TRANSFER_IN,
    INTEREST_CREDIT,
    /** Balance paid out when an account is closed. */
    CLOSURE_PAYOUT;

    public boolean isTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }
}
