package com.example.lms.controller;

import com.example.lms.dto.request.IssueBookRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.IssueResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.entity.LoanStatus;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.IssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
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

import java.util.function.Function;

@RestController
@RequestMapping("/api/issues")
@Tag(name = "Issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    /**
     * Issues a book.
     *
     * <p>Staff only, deliberately. A member cannot issue a book to themselves:
     * the copy has to physically leave the desk, and an endpoint that lets a
     * member mark a book as borrowed without anyone handing it over produces a
     * catalogue that disagrees with the shelves.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Issue a book to a member")
    public ResponseEntity<ApiResponse<IssueResponse>> issue(
            @Valid @RequestBody IssueBookRequest request) {
        IssueResponse issued = issueService.issue(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(issued, "Due " + issued.dueDate()));
    }

    @GetMapping
    public ApiResponse<PageResponse<IssueResponse>> list(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                issueService.list(status, memberId, bookId,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<IssueResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                issueService.list(null, SecurityUtils.currentMemberId(), null,
                        PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    /** A member may read their own loans; anything else is a librarian's business. */
    @GetMapping("/{id}")
    public ApiResponse<IssueResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(issueService.get(id));
    }

    @GetMapping("/reference/{reference}")
    public ApiResponse<IssueResponse> getByReference(@PathVariable String reference) {
        return ApiResponse.ok(issueService.getByReference(reference));
    }
}
