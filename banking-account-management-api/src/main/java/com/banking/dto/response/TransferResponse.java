package com.banking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The outcome of a transfer, showing both halves.
 *
 * <p>A transfer writes two ledger rows sharing one reference. Returning only the
 * debit would hide that, and the customer's obvious next question — did it
 * arrive — would have no answer in the response.
 */
public record TransferResponse(String transactionReference, String fromAccountNumber,
                               String toAccountNumber, String toAccountName,
                               BigDecimal amount, BigDecimal sourceBalanceAfter,
                               Instant transactedAt, String description) {
}
