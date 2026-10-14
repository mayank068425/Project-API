package com.supportai.controller;

import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.PageResponse;
import com.supportai.service.AuditService;
import com.supportai.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;

/**
 * The audit trail.
 *
 * <p>Lead and admin only: it records who asked the assistant what, which is
 * enough to reconstruct a colleague's line of investigation.
 */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('LEAD','ADMIN')")
@Tag(name = "Audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Audit events",
            description = "Assistant calls record a SHA-256 of the prompt, never the prompt "
                    + "itself: incident text contains customer data and a long-retention "
                    + "table is the wrong place for a second copy. The retrieved chunk ids "
                    + "and verification outcome are recorded instead.")
    public ApiResponse<PageResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                auditService.list(actor, action,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/assistant-health")
    @Operation(summary = "Verification rejection rate",
            description = "A rising rate usually means retrieval has degraded — a re-index "
                    + "with a different embedding model, or a document set that has drifted "
                    + "from what people ask — rather than a model problem.")
    public ApiResponse<Map<String, Object>> assistantHealth(
            @RequestParam(defaultValue = "7") int days) {
        double rate = auditService.rejectionRate(days);
        return ApiResponse.ok(Map.of("days", days, "rejectionRate", rate),
                rate > 0.2 ? "Elevated. Check whether documents were re-indexed with a "
                        + "different embedding model." : null);
    }
}
