package com.banking.entity;

public enum AccountStatus {

    ACTIVE,
    /** Blocked by the bank. Neither debits nor credits. */
    FROZEN,
    /** Closed at zero balance. Never transacts again; kept for history. */
    CLOSED,
    /** Opened but not yet funded or approved. */
    INACTIVE;

    public boolean canDebit() {
        return this == ACTIVE;
    }

    /**
     * Whether the account may receive money.
     *
     * <p>A closed account must refuse credits too. Crediting one puts money
     * somewhere nobody can withdraw it from, and it is a support case that takes
     * a human to unpick.
     */
    public boolean canCredit() {
        return this == ACTIVE;
    }
}
