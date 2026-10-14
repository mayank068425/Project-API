package com.example.lms.controller;

import com.example.lms.dto.request.MemberRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.IssueResponse;
import com.example.lms.dto.response.MemberResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.entity.MembershipStatus;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.IssueService;
import com.example.lms.service.MemberService;
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

import java.util.function.Function;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Members")
public class MemberController {

    private final MemberService memberService;
    private final IssueService issueService;

    public MemberController(MemberService memberService, IssueService issueService) {
        this.memberService = memberService;
        this.issueService = issueService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<PageResponse<MemberResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) MembershipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                memberService.search(q, status, PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ResponseEntity<ApiResponse<MemberResponse>> register(
            @Valid @RequestBody MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(memberService.register(request)));
    }

    @GetMapping("/me")
    public ApiResponse<MemberResponse> me() {
        return ApiResponse.ok(memberService.get(SecurityUtils.currentMemberId()));
    }

    /** Ownership is checked in the service: a member may read only their own record. */
    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(memberService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MemberResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody MemberRequest request) {
        return ApiResponse.ok(memberService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<MemberResponse> setStatus(@PathVariable Long id,
                                                 @RequestParam MembershipStatus status,
                                                 @RequestParam(required = false) String reason) {
        return ApiResponse.ok(memberService.setStatus(id, status, reason));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Cancel a membership. Refused while books are out or fines unpaid.")
    public ApiResponse<String> deactivate(@PathVariable Long id,
                                          @RequestParam(required = false) String reason) {
        memberService.deactivate(id, reason);
        return ApiResponse.ok("Membership cancelled");
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Borrowing history. Members see only their own.")
    public ApiResponse<PageResponse<IssueResponse>> history(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                issueService.list(null, id, null, PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }
}
