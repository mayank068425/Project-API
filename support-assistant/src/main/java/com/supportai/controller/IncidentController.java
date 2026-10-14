package com.supportai.controller;

import com.supportai.dto.request.IncidentRequest;
import com.supportai.dto.request.TimelineEntryRequest;
import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.IncidentResponse;
import com.supportai.dto.response.PageResponse;
import com.supportai.dto.response.TimelineEntryResponse;
import com.supportai.entity.IncidentStatus;
import com.supportai.service.IncidentService;
import com.supportai.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IncidentResponse>> create(
            @Valid @RequestBody IncidentRequest request) {
        IncidentResponse created = incidentService.create(request);
        return ResponseEntity.created(java.net.URI.create("/api/incidents/" + created.id()))
                .body(ApiResponse.ok(created));
    }

    @GetMapping
    public ApiResponse<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                incidentService.list(status, service,
                        PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE))),
                Function.identity()));
    }

    @GetMapping("/open")
    @Operation(summary = "Everything currently open, worst severity first")
    public ApiResponse<List<IncidentResponse>> open() {
        return ApiResponse.ok(incidentService.open());
    }

    @GetMapping("/{id}")
    public ApiResponse<IncidentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(incidentService.get(id));
    }

    @GetMapping("/number/{incidentNumber}")
    public ApiResponse<IncidentResponse> getByNumber(@PathVariable String incidentNumber) {
        return ApiResponse.ok(incidentService.getByNumber(incidentNumber));
    }

    @PutMapping("/{id}")
    public ApiResponse<IncidentResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody IncidentRequest request) {
        return ApiResponse.ok(incidentService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an incident through its lifecycle",
            description = "MITIGATED is separate from RESOLVED: customer impact usually "
                    + "stops well before the fault is fixed. Pass `at` to backdate an "
                    + "acknowledgement made before anyone opened the tool.")
    public ApiResponse<IncidentResponse> transition(
            @PathVariable Long id,
            @RequestParam IncidentStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ApiResponse.ok(incidentService.transition(id, status, at));
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "The timeline, in the order things happened",
            description = "Ordered by occurrence, not insertion. Entries marked "
                    + "evidence=false were written by the assistant and cannot be cited "
                    + "by a generated analysis.")
    public ApiResponse<List<TimelineEntryResponse>> timeline(@PathVariable Long id) {
        return ApiResponse.ok(incidentService.timeline(id));
    }

    @PostMapping("/{id}/timeline")
    @Operation(summary = "Add a timeline entry",
            description = "Pass occurredAt when writing up something that happened earlier. "
                    + "Insertion order is not event order, and the RCA reasons from this.")
    public ResponseEntity<ApiResponse<TimelineEntryResponse>> addEntry(
            @PathVariable Long id, @Valid @RequestBody TimelineEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(incidentService.addTimelineEntry(id, request)));
    }
}
