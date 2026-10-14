package com.banking.dto.response;

import com.banking.entity.AccountStatus;
import com.banking.entity.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param availableBalance balance less the minimum balance. Shown alongside the
 *                         raw balance because they differ, and the available
 *                         figure is the one a customer can act on.
 */
public record AccountResponse(Long id, String accountNumber, Long customerId,
                              String customerName, AccountType accountType,
                              AccountStatus status, BigDecimal balance,
                              BigDecimal availableBalance, BigDecimal minimumBalance,
                              BigDecimal interestRate, BigDecimal dailyTransferLimit,
                              LocalDate openedOn, LocalDate closedOn) {
}
