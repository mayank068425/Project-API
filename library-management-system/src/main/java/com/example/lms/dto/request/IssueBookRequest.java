package com.example.lms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IssueBookRequest(
        @NotNull(message = "Book is required") Long bookId,
        @NotNull(message = "Member is required") Long memberId,
        @Size(max = 300) String remarks) {
}
