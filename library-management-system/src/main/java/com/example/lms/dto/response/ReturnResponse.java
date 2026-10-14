package com.example.lms.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The outcome of a return.
 *
 * @param fineExplanation the workings, not just a total. A member disputing a
 *                        charge wants "fourteen days at two a day, capped at the
 *                        replacement cost", and the desk should not have to
 *                        recompute it by hand.
 * @param heldForReservation whether the copy went onto a hold rather than back
 *                        to the shelf. The librarian needs to know before
 *                        offering it to whoever is standing there.
 */
public record ReturnResponse(Long id, String reference, String issueReference,
                             String bookTitle, String memberName,
                             Instant returnedAt, LocalDate returnedOn, LocalDate dueDate,
                             int daysLate, String condition,
                             boolean fineRaised, Long fineId, BigDecimal fineAmount,
                             boolean fineCapped, String fineExplanation,
                             boolean heldForReservation, String heldForMember) {
}
