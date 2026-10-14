package com.example.lms.mapper;

import com.example.lms.dto.response.IssueResponse;
import com.example.lms.entity.BookIssue;
import org.springframework.stereotype.Component;

/**
 * Loan entity to response.
 *
 * <p>Requires book and member to be fetched. {@code bookReturn} is deliberately
 * <em>not</em> touched — testing it for null would trigger a lazy load per row,
 * which is the N+1 this whole DTO layer exists to avoid. Whether the loan is
 * closed is read from its status instead, which is on the row already.
 *
 * @see com.example.lms.repository.BookIssueRepository#findByIdWithRelations
 */
@Component
public class IssueMapper {

    public IssueResponse toResponse(BookIssue issue) {
        if (issue == null) {
            return null;
        }
        return new IssueResponse(
                issue.getId(), issue.getReference(),
                issue.getBook().getId(), issue.getBook().getTitle(), issue.getBook().getIsbn(),
                issue.getMember().getId(), issue.getMember().getName(),
                issue.getMember().getMembershipNumber(),
                issue.getIssueDate(), issue.getDueDate(), issue.getStatus(),
                issue.getRenewalCount(), issue.getIssuedBy(),
                issue.getStatus() == com.example.lms.entity.LoanStatus.RETURNED);
    }
}
