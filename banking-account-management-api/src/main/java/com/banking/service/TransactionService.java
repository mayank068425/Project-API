package com.banking.service;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.StatementResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.dto.response.TransferResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface TransactionService {

    TransactionResponse deposit(DepositRequest request);

    TransactionResponse withdraw(WithdrawRequest request);

    /**
     * Moves money between two accounts.
     *
     * <p>Writes two ledger rows — a debit against the source and a credit against
     * the destination — sharing one reference, in one database transaction. Both
     * accounts are locked in id order, so two simultaneous transfers between the
     * same pair in opposite directions cannot deadlock.
     */
    TransferResponse transfer(TransferRequest request);

    TransactionResponse get(Long id);

    /** Both halves of a transfer, or the single row for anything else. */
    List<TransactionResponse> byReference(String reference);

    Page<TransactionResponse> history(String accountNumber, Pageable pageable);

    Page<TransactionResponse> all(Pageable pageable);

    /** The most recent handful of movements. */
    List<TransactionResponse> miniStatement(String accountNumber);

    /** A period statement with opening and closing balances, so it can be checked. */
    StatementResponse statement(String accountNumber, LocalDate from, LocalDate to);

    StatementResponse monthlyStatement(String accountNumber, int year, int month);
}
