package com.example.lms.service;

import com.example.lms.dto.request.IssueBookRequest;
import com.example.lms.dto.response.IssueResponse;
import com.example.lms.entity.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IssueService {

    IssueResponse issue(IssueBookRequest request);

    IssueResponse get(Long id);

    IssueResponse getByReference(String reference);

    Page<IssueResponse> list(LoanStatus status, Long memberId, Long bookId, Pageable pageable);

    /** Labels outstanding loans past their due date. Reporting only. */
    int markOverdue();
}
