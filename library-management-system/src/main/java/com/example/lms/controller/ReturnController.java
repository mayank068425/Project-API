package com.example.lms.controller;

import com.example.lms.dto.request.ReturnBookRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.PageResponse;
import com.example.lms.dto.response.ReturnResponse;
import com.example.lms.service.ReturnService;
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

import java.util.function.Function;

@RestController
@RequestMapping("/api/returns")
@Tag(name = "Returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    @Operation(summary = "Take a book back",
            description = "Assesses any fine and decides where the copy goes. If members "
                    + "are queued for the title the copy is held for the first of them "
                    + "rather than shelved, so a successful return does not necessarily "
                    + "make the book available.")
    public ApiResponse<ReturnResponse> returnBook(
            @Valid @RequestBody ReturnBookRequest request) {
        ReturnResponse response = returnService.returnBook(request);

        // Surfaced as a message rather than left in a boolean field, because the
        // librarian is holding the physical book and needs to know it goes behind
        // the desk, not back into the stacks.
        String message = response.heldForReservation()
                ? "This copy is being held for a member in the reservation queue; "
                        + "do not shelve it."
                : null;
        return ApiResponse.ok(response, message);
    }

    @GetMapping
    public ApiResponse<PageResponse<ReturnResponse>> list(
            @RequestParam(required = false) Long memberId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                returnService.list(memberId, PageRequest.of(page, Math.min(size, 100))),
                Function.identity()));
    }

    @GetMapping("/{reference}")
    public ApiResponse<ReturnResponse> getByReference(@PathVariable String reference) {
        return ApiResponse.ok(returnService.getByReference(reference));
    }
}
