package com.banking.dto.response;

import com.banking.entity.TransactionDirection;
import com.banking.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One ledger row.
 *
 * @param signedAmount amount with the direction applied, so a client can sum a
 *                     statement without reimplementing the sign rule and getting
 *                     it subtly wrong for a transaction type it has not seen
 * @param balanceAfter the running balance, stored rather than recomputed
 */
public record TransactionResponse(Long id, String transactionReference, String accountNumber,
                                  TransactionType transactionType,
                                  TransactionDirection direction, BigDecimal amount,
                                  BigDecimal signedAmount, BigDecimal balanceAfter,
                                  String counterpartyAccount, String counterpartyName,
                                  String description, Instant transactedAt,
                                  LocalDate valueDate, String performedBy) {
}
