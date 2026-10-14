package com.banking.entity;

import java.math.BigDecimal;

/**
 * The product a customer holds.
 *
 * <p>The minimum balance carried here is a <em>default</em>. The real figure
 * lives on the account row, because a bank waives the floor for staff, students
 * and legacy products, and a rule that cannot be waived per-account is a rule
 * somebody works around with a fake account type.
 */
public enum AccountType {

    SAVINGS(new BigDecimal("1000.00"), new BigDecimal("3.50")),
    CURRENT(new BigDecimal("5000.00"), BigDecimal.ZERO),
    /** No floor: a salary account that cannot be emptied is not a salary account. */
    SALARY(BigDecimal.ZERO, new BigDecimal("3.00"));

    private final BigDecimal defaultMinimumBalance;
    private final BigDecimal defaultAnnualInterestRate;

    AccountType(BigDecimal defaultMinimumBalance, BigDecimal defaultAnnualInterestRate) {
        this.defaultMinimumBalance = defaultMinimumBalance;
        this.defaultAnnualInterestRate = defaultAnnualInterestRate;
    }

    public BigDecimal getDefaultMinimumBalance() {
        return defaultMinimumBalance;
    }

    public BigDecimal getDefaultAnnualInterestRate() {
        return defaultAnnualInterestRate;
    }

    public boolean earnsInterest() {
        return defaultAnnualInterestRate.compareTo(BigDecimal.ZERO) > 0;
    }
}
