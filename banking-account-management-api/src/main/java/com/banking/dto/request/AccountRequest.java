package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Open an account.
 *
 * @param accountType SAVINGS, CURRENT or SALARY
 * @param minimumBalance optional override of the product default. A bank waives
 *                       the floor for staff and legacy products, and a rule that
 *                       cannot be waived per account is one somebody works
 *                       around by inventing a fake product.
 * @param initialDeposit optional. Recorded as a real DEPOSIT transaction rather
 *                       than written straight onto the balance, or the account's
 *                       first statement starts with money that came from nowhere.
 */
public record AccountRequest(
        @NotNull(message = "Customer is required") Long customerId,
        @NotNull(message = "Account type is required") String accountType,
        @DecimalMin(value = "0.00") @Digits(integer = 15, fraction = 4)
        BigDecimal minimumBalance,
        @DecimalMin(value = "0.00") @Digits(integer = 15, fraction = 4)
        BigDecimal initialDeposit,
        @DecimalMin(value = "0.00") @Digits(integer = 15, fraction = 4)
        BigDecimal dailyTransferLimit) {
}
