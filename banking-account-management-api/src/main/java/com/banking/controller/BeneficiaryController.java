package com.banking.controller;

import com.banking.dto.request.BeneficiaryRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.BeneficiaryResponse;
import com.banking.dto.response.PageResponse;
import com.banking.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.function.Function;

@RestController
@RequestMapping("/api/beneficiaries")
@Tag(name = "Beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    @Operation(summary = "Save a payee",
            description = "Created unverified. Nothing can be sent to it until it is "
                    + "verified, and for a cooling-off window after that, only small "
                    + "amounts.")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> add(
            @Valid @RequestBody BeneficiaryRequest request) {
        BeneficiaryResponse created = beneficiaryService.add(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created,
                "Payee saved. Verify it before sending money."));
    }

    @GetMapping
    @Operation(summary = "Your saved payees. Account numbers are masked.")
    public ApiResponse<PageResponse<BeneficiaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                beneficiaryService.list(PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @GetMapping("/{id}")
    public ApiResponse<BeneficiaryResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(beneficiaryService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a payee's label",
            description = "The account number is deliberately not editable: changing it "
                    + "would repoint a verified payee at somewhere nobody confirmed. A "
                    + "new destination is a new payee.")
    public ApiResponse<BeneficiaryResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody BeneficiaryRequest request) {
        return ApiResponse.ok(beneficiaryService.update(id, request));
    }

    @PatchMapping("/{id}/verify")
    @Operation(summary = "Verify a payee against the bank's records",
            description = "The confirmed name is taken from the account, not from what "
                    + "was typed, so a mistyped number and name cannot look correct.")
    public ApiResponse<BeneficiaryResponse> verify(@PathVariable Long id) {
        BeneficiaryResponse verified = beneficiaryService.verify(id);
        return ApiResponse.ok(verified, "Verified as " + verified.beneficiaryName()
                + ". Large transfers are held briefly as a fraud check.");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a payee. Soft, so past transfers stay explainable.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        beneficiaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
