package com.banking.controller;

import com.banking.dto.request.CustomerRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.CustomerResponse;
import com.banking.dto.response.PageResponse;
import com.banking.entity.KycStatus;
import com.banking.security.SecurityUtils;
import com.banking.service.CustomerService;
import com.banking.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/customers/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Search customers by name, email, phone or customer number")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                customerService.search(q,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/me")
    public ApiResponse<CustomerResponse> me() {
        return ApiResponse.ok(customerService.get(SecurityUtils.currentCustomerId()));
    }

    /** Ownership is checked in the service: a customer may read only their own record. */
    @GetMapping("/{id}")
    public ApiResponse<CustomerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(customerService.get(id));
    }

    @GetMapping("/{id}/accounts")
    @Operation(summary = "Accounts held by this customer")
    public ApiResponse<List<AccountResponse>> accounts(@PathVariable Long id) {
        return ApiResponse.ok(customerService.accountsOf(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CustomerResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok(customerService.update(id, request));
    }

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasAnyRole('BANK_EMPLOYEE','ADMIN')")
    @Operation(summary = "Set KYC status. Gates account opening.")
    public ApiResponse<CustomerResponse> setKyc(@PathVariable Long id,
                                                @RequestParam KycStatus status,
                                                @RequestParam(required = false) String reason) {
        return ApiResponse.ok(customerService.setKycStatus(id, status, reason));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a customer",
            description = "Soft delete, refused while any account is open. The record "
                    + "anchors every transaction they ever made.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
