package com.example.lms.dto.response;

import com.example.lms.entity.LoanStatus;

import java.time.LocalDate;

public record IssueResponse(Long id, String reference, Long bookId, String bookTitle,
                            String isbn, Long memberId, String memberName,
                            String membershipNumber, LocalDate issueDate, LocalDate dueDate,
                            LoanStatus status, int renewalCount, String issuedBy,
                            boolean returned) {
}
