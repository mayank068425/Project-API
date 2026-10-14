package com.banking.controller;

import com.banking.dto.request.AccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.AccountStatus;
import com.banking.service.AccountService;
import com.banking.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Open an account",
            description = "Requires the customer's KYC to be VERIFIED. Any opening "
                    + "deposit is recorded as a real transaction, not written onto the "
                    + "balance, so the first statement does not begin with money from "
                    + "nowhere.")
    public ResponseEntity<ApiResponse<AccountResponse>> open(
            @Valid @RequestBody AccountRequest request) {
        AccountResponse created = accountService.open(request);
        return ResponseEntity.created(java.net.URI.create("/api/accounts/" + created.id()))
                .body(ApiResponse.ok(created,
                        "Account " + created.accountNumber() + " opened"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ApiResponse<PageResponse<AccountResponse>> list(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                accountService.list(status,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    /** Ownership is checked in the service. */
    @GetMapping("/{id}")
    public ApiResponse<AccountResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(accountService.get(id));
    }

    @GetMapping("/number/{accountNumber}")
    public ApiResponse<AccountResponse> getByNumber(@PathVariable String accountNumber) {
        return ApiResponse.ok(accountService.getByAccountNumber(accountNumber));
    }

    @GetMapping("/number/{accountNumber}/balance")
    @Operation(summary = "Current balance")
    public ApiResponse<Map<String, BigDecimal>> balance(@PathVariable String accountNumber) {
        return ApiResponse.ok(Map.of("balance", accountService.balanceOf(accountNumber)));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Accounts held by a customer")
    public ApiResponse<PageResponse<AccountResponse>> byCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                accountService.byCustomer(customerId,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    /**
     * Maintenance actions.
     *
     * <p>{@code PUT /{id}} is the general update in the specified API, and here
     * it dispatches on the requested status. Freezing and activating also have
     * their own PATCH endpoints, because "freeze this account" is a specific
     * action with its own audit meaning rather than a field assignment.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Change account status: ACTIVE or FROZEN")
    public ApiResponse<AccountResponse> update(@PathVariable Long id,
                                               @RequestParam AccountStatus status,
                                               @RequestParam(required = false) String reason) {
        return switch (status) {
            case FROZEN -> ApiResponse.ok(accountService.freeze(id, reason));
            case ACTIVE -> ApiResponse.ok(accountService.activate(id));
            // Closing has its own endpoint because it moves money: it pays out
            // the remaining balance first. Hiding that behind a status change
            // would be a surprising thing for a PUT to do.
            case CLOSED -> throw new com.banking.exception.BusinessRuleException(
                    "Use DELETE /api/accounts/{id} to close an account; "
                            + "closing pays out any remaining balance");
            case INACTIVE -> throw new com.banking.exception.BusinessRuleException(
                    "Accounts cannot be set back to INACTIVE");
        };
    }

    @PatchMapping("/{id}/freeze")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Freeze an account",
            description = "Blocks debits and credits. Blocking only debits would let "
                    + "money keep arriving in an account under investigation.")
    public ApiResponse<AccountResponse> freeze(@PathVariable Long id,
                                               @RequestParam(required = false) String reason) {
        return ApiResponse.ok(accountService.freeze(id, reason));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ApiResponse<AccountResponse> activate(@PathVariable Long id) {
        return ApiResponse.ok(accountService.activate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Close an account",
            description = "Pays out any remaining balance as a CLOSURE_PAYOUT "
                    + "transaction, then closes. The minimum balance is waived on this "
                    + "path, or a savings account could never be closed.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Closed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "Frozen, already closed, or overdrawn")
    })
    public ApiResponse<AccountResponse> close(@PathVariable Long id) {
        AccountResponse closed = accountService.close(id);
        return ApiResponse.ok(closed, "Account closed");
    }
}
