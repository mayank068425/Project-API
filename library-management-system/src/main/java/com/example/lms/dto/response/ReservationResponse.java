package com.example.lms.dto.response;

import com.example.lms.entity.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(Long id, Long bookId, String bookTitle, Long memberId,
                                  String memberName, ReservationStatus status,
                                  Instant reservedAt, Instant holdExpiresAt,
                                  Instant expiresAt, long queuePosition) {
}
