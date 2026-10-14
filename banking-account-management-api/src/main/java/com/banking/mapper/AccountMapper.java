package com.banking.mapper;

import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.util.Constants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Account entity to response.
 *
 * <p>Requires the customer association to be loaded — the response carries the
 * holder's name. Use {@code AccountRepository.findByIdWithCustomer} or one of
 * the paged fetch-join queries; touching it lazily here, after the transaction
 * has closed and with {@code open-in-view} off, throws.
 *
 * <p>Balances are rounded to presentation scale exactly here, at the boundary.
 * Storage keeps four decimal places for interest accrual, and rounding
 * repeatedly along the way is how a ledger ends up a cent adrift.
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        if (account == null) {
            return null;
        }
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomer().getId(),
                account.getCustomer().getFullName(),
                account.getAccountType(),
                account.getStatus(),
                present(account.getBalance()),
                present(account.availableBalance()),
                present(account.getMinimumBalance()),
                account.getInterestRate(),
                account.getDailyTransferLimit() == null
                        ? null : present(account.getDailyTransferLimit()),
                account.getOpenedOn(),
                account.getClosedOn());
    }

    private BigDecimal present(BigDecimal value) {
        return value == null ? null
                : value.setScale(Constants.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
