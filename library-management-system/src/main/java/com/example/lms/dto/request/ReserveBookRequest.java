package com.example.lms.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * @param memberId honoured only for staff placing a reservation at the counter;
 *                 a member always reserves for themselves
 */
public record ReserveBookRequest(@NotNull Long bookId, Long memberId) {
}
