package com.example.lms.service;

import com.example.lms.dto.response.FineResponse;
import com.example.lms.entity.FineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface FineService {

    /**
     * Everything a member currently owes.
     *
     * <p>Two different things, deliberately: fines already raised on returned
     * books, and charges still accruing on books that are out and overdue and
     * have no fine row yet. Showing only the first tells a member with an overdue
     * book at home that they owe nothing.
     */
    List<FineResponse> pendingFor(Long memberId);

    BigDecimal outstandingTotal(Long memberId);

    FineResponse pay(Long fineId, BigDecimal amount);

    /** Requires a reason: it is the one action that makes money disappear. */
    FineResponse waive(Long fineId, String reason);

    Page<FineResponse> history(Long memberId, Pageable pageable);

    Page<FineResponse> byStatus(FineStatus status, Pageable pageable);
}
