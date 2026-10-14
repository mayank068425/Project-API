package com.example.lms.dto.response;

import com.example.lms.entity.BookStatus;

import java.math.BigDecimal;

/**
 * A book as it leaves the API.
 *
 * <p>Entities are never serialised. {@code Book} points at {@code Author} and
 * {@code Category}, and returning it directly would send Jackson through those
 * associations — triggering lazy loads outside the transaction at best, and
 * exposing {@code version} and audit columns at worst. A record makes the wire
 * format explicit and independent of the persistence model.
 */
public record BookResponse(Long id, String isbn, String title,
                           Long authorId, String authorName,
                           Long categoryId, String categoryName,
                           String publisher, String edition, String language,
                           String shelfNumber, Integer publicationYear,
                           int totalCopies, int availableCopies, int heldCopies,
                           int issuedCopies, BigDecimal replacementCost,
                           BookStatus status, boolean available) {
}
