package com.example.lms.controller;

import com.example.lms.dto.request.PayFineRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.FineResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.entity.FineStatus;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.FineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/fines")
@Tag(name = "Fines")
public class FineController {

    private final FineService fineService;

    public FineController(FineService fineService) {
        this.fineService = fineService;
    }

    /**
     * Fines, scoped to who is asking.
     *
     * <p>A member calling this without a memberId gets their own pending charges,
     * which is what they meant. Staff get the full list.
     */
    @GetMapping
    public ApiResponse<Object> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) FineStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (memberId == null && !SecurityUtils.isStaff()) {
            return ApiResponse.ok(fineService.pendingFor(SecurityUtils.currentMemberId()));
        }
        if (memberId != null) {
            return ApiResponse.ok(PageResponse.from(
                    fineService.history(memberId, PageRequest.of(page, Math.min(size, 100))),
                    Function.identity()));
        }
        return ApiResponse.ok(PageResponse.from(
                fineService.byStatus(status == null ? FineStatus.PENDING : status,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @GetMapping("/pending")
    @Operation(summary = "What a member owes now",
            description = "Includes charges still accruing on books they have not "
                    + "returned. Showing only raised fines tells a member with an overdue "
                    + "book at home that they owe nothing.")
    public ApiResponse<List<FineResponse>> pending(
            @RequestParam(required = false) Long memberId) {
        Long target = memberId != null ? memberId : SecurityUtils.currentMemberId();
        return ApiResponse.ok(fineService.pendingFor(target),
                "Total outstanding: " + fineService.outstandingTotal(target));
    }

    @PostMapping("/pay")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Take payment",
            description = "Partial payments are allowed; the fine closes only when the "
                    + "balance reaches zero.")
    public ApiResponse<FineResponse> pay(@Valid @RequestBody PayFineRequest request) {
        FineResponse fine = fineService.pay(request.fineId(), request.amount());
        return ApiResponse.ok(fine, fine.status() == FineStatus.PAID
                ? "Settled in full"
                : fine.outstanding() + " still outstanding");
    }

    @PostMapping("/{id}/waive")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Waive a fine. A reason is required and goes to the audit log.")
    public ApiResponse<FineResponse> waive(@PathVariable Long id,
                                           @RequestParam String reason) {
        return ApiResponse.ok(fineService.waive(id, reason));
    }

    @GetMapping("/member/{memberId}/total")
    public ApiResponse<BigDecimal> outstandingTotal(@PathVariable Long memberId) {
        return ApiResponse.ok(fineService.outstandingTotal(memberId));
    }
}
