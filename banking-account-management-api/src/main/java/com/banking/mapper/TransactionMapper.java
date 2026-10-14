package com.banking.mapper;

import com.banking.dto.response.TransactionResponse;
import com.banking.entity.Transaction;
import com.banking.util.Constants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Transaction entity to response.
 *
 * <p>Requires the account association to be loaded, for the account number.
 * Deliberately does <em>not</em> reach through to the customer: a statement of a
 * thousand rows would trigger a thousand lazy loads, which is the N+1 this whole
 * DTO layer exists to prevent.
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }
        return new TransactionResponse(
                transaction.getId(),
                transaction.getTransactionReference(),
                transaction.getAccount().getAccountNumber(),
                transaction.getTransactionType(),
                transaction.getDirection(),
                present(transaction.getAmount()),
                // Signed here so a client summing a statement does not have to
                // reimplement the direction rule and get it wrong for a
                // transaction type it has not seen before.
                present(transaction.signedAmount()),
                present(transaction.getBalanceAfter()),
                transaction.getCounterpartyAccount(),
                transaction.getCounterpartyName(),
                transaction.getDescription(),
                transaction.getTransactedAt(),
                transaction.getValueDate(),
                transaction.getPerformedBy());
    }

    private BigDecimal present(BigDecimal value) {
        return value == null ? null
                : value.setScale(Constants.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
