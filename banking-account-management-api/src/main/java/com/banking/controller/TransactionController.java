package com.banking.controller;

import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.PageResponse;
import com.banking.dto.response.StatementResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.dto.response.TransferResponse;
import com.banking.service.TransactionService;
import com.banking.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Cash in.
     *
     * <p>Staff only. Cash arrives at a counter or a machine; a customer cannot
     * credit their own account from a session, or the balance stops corresponding
     * to money the bank actually holds.
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Deposit cash into an account")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(transactionService.deposit(request)));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Withdraw cash",
            description = "Refused if the balance is too low or if the withdrawal would "
                    + "breach the account's minimum balance. The response says which.")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(transactionService.withdraw(request)));
    }

    /**
     * Move money between accounts.
     *
     * <p>Customer-initiated, unlike cash. The source must belong to the caller,
     * and if they have saved the destination as a payee, that payee's
     * verification state and cooling-off window apply.
     */
    @PostMapping("/transfer")
    @Operation(summary = "Transfer between accounts",
            description = "Writes two ledger rows sharing one reference, in one "
                    + "transaction. Both accounts are locked in id order so simultaneous "
                    + "transfers in opposite directions cannot deadlock.")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transactionService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response,
                "Sent to " + response.toAccountName()));
    }

    @GetMapping
    @Operation(summary = "Transactions. Staff see all; a customer sees their own.")
    public ApiResponse<PageResponse<TransactionResponse>> all(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                transactionService.all(
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.get(id));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Both halves of a transfer",
            description = "A transfer is two rows sharing one reference. A payer sees "
                    + "both; anyone else sees only their own side.")
    public ApiResponse<List<TransactionResponse>> byReference(@PathVariable String reference) {
        return ApiResponse.ok(transactionService.byReference(reference));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Transaction history for an account")
    public ApiResponse<PageResponse<TransactionResponse>> history(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                transactionService.history(accountNumber,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/account/{accountNumber}/mini-statement")
    @Operation(summary = "The last ten movements")
    public ApiResponse<List<TransactionResponse>> miniStatement(
            @PathVariable String accountNumber) {
        return ApiResponse.ok(transactionService.miniStatement(accountNumber));
    }

    @GetMapping("/account/{accountNumber}/statement")
    @Operation(summary = "Statement for a period",
            description = "Includes opening and closing balances so the arithmetic is "
                    + "checkable: opening + credits - debits should equal closing.")
    public ApiResponse<StatementResponse> statement(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(transactionService.statement(accountNumber, from, to));
    }

    @GetMapping("/account/{accountNumber}/statement/{year}/{month}")
    @Operation(summary = "Monthly statement",
            description = "Bounded by value date in the bank's timezone, so a "
                    + "transaction at 23:55 on the 31st stays in that month.")
    public ApiResponse<StatementResponse> monthlyStatement(
            @PathVariable String accountNumber,
            @PathVariable int year,
            @PathVariable int month) {
        return ApiResponse.ok(transactionService.monthlyStatement(accountNumber, year, month));
    }
}
