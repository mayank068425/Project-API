package com.banking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A statement for a period.
 *
 * <p>Opening and closing balances are included because a statement that only
 * lists movements cannot be checked. With both, the arithmetic is verifiable by
 * the reader: opening + credits − debits should equal closing, and if it does
 * not, the statement itself is telling you something is wrong.
 */
public record StatementResponse(String accountNumber, String customerName,
                                LocalDate fromDate, LocalDate toDate,
                                BigDecimal openingBalance, BigDecimal closingBalance,
                                BigDecimal totalCredits, BigDecimal totalDebits,
                                int transactionCount, List<TransactionResponse> transactions) {
}
