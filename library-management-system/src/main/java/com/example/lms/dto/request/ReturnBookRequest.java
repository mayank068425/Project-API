package com.example.lms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param condition GOOD, MINOR_DAMAGE, DAMAGED or LOST. Recorded on the return
 *                  so the next borrower is not blamed for existing damage.
 */
public record ReturnBookRequest(
        @NotBlank(message = "Issue reference is required") String reference,
        String condition,
        @Size(max = 300) String remarks) {
}
