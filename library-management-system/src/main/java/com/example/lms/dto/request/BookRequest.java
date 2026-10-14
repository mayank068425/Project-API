package com.example.lms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create or update a book.
 *
 * <p>One record for both, with everything except the ISBN nullable. On update a
 * null field means "leave it alone", which is what lets a caller change a shelf
 * number without resending the whole record. {@code BookValidator} enforces the
 * fields that are mandatory on create, because a bean-validation annotation
 * cannot say "required here, optional there".
 */
public record BookRequest(
        @Pattern(regexp = "^(?:\\d[- ]?){9}[\\dXx]$|^(?:\\d[- ]?){12}\\d$",
                message = "Must be a valid ISBN-10 or ISBN-13") String isbn,
        @Size(max = 300) String title,
        Long authorId,
        Long categoryId,
        @Size(max = 200) String publisher,
        @Size(max = 40) String edition,
        @Size(max = 40) String language,
        @Size(max = 30) String shelfNumber,
        Integer publicationYear,
        @Min(value = 0, message = "Total copies cannot be negative") Integer totalCopies,
        @DecimalMin(value = "0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal replacementCost,
        String status) {
}
