package com.banking.service;

import com.banking.dto.request.AccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface AccountService {

    AccountResponse open(AccountRequest request);

    /**
     * Closes an account.
     *
     * <p>Requires a zero balance, and pays out any remainder as a real
     * CLOSURE_PAYOUT transaction first. The minimum balance is waived on this
     * path specifically — a savings account with a floor could otherwise never
     * reach the zero balance closing requires.
     */
    AccountResponse close(Long id);

    AccountResponse freeze(Long id, String reason);

    AccountResponse activate(Long id);

    AccountResponse get(Long id);

    AccountResponse getByAccountNumber(String accountNumber);

    BigDecimal balanceOf(String accountNumber);

    Page<AccountResponse> list(AccountStatus status, Pageable pageable);

    Page<AccountResponse> byCustomer(Long customerId, Pageable pageable);

    /** For the transaction path, which needs the entity rather than a response. */
    Account requireAccount(String accountNumber);
}
