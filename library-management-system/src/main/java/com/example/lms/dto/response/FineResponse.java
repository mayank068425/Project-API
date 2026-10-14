package com.example.lms.dto.response;

import com.example.lms.entity.Fine;
import com.example.lms.entity.FineStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FineResponse(Long id, String issueReference, Long memberId, String memberName,
                           int daysOverdue, BigDecimal ratePerDay, BigDecimal calculatedAmount,
                           BigDecimal amount, BigDecimal amountPaid, BigDecimal outstanding,
                           boolean capped, FineStatus status, Instant paidAt,
                           String collectedBy, String waivedReason) {

    public static FineResponse from(Fine fine, String issueReference, String memberName) {
        return new FineResponse(fine.getId(), issueReference, fine.getMember().getId(),
                memberName, fine.getDaysOverdue(), fine.getRatePerDay(),
                fine.getCalculatedAmount(), fine.getAmount(), fine.getAmountPaid(),
                fine.outstanding(), fine.isCapped(), fine.getStatus(), fine.getPaidAt(),
                fine.getCollectedBy(), fine.getWaivedReason());
    }
}
