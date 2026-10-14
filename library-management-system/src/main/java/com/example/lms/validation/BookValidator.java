package com.example.lms.validation;

import com.example.lms.dto.request.BookRequest;
import com.example.lms.entity.Book;
import com.example.lms.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Rules a Bean Validation annotation cannot express.
 *
 * <p>Three kinds live here. Fields that are mandatory on create and optional on
 * update, which no single annotation can say. Rules that depend on the current
 * state of the row being changed. And checks that need a second field to be
 * meaningful.
 */
@Component
public class BookValidator {

    private static final int EARLIEST_PUBLICATION_YEAR = 1450;

    /**
     * Everything required to create a book.
     *
     * <p>The same {@link BookRequest} is used for update, where a null field
     * means "leave it alone". That is what lets a caller change a shelf number
     * without resending the whole record, and it is why these cannot simply be
     * {@code @NotNull} on the record.
     */
    public void validateForCreate(BookRequest request) {
        requirePresent(request.isbn(), "ISBN");
        requirePresent(request.title(), "Title");

        if (request.authorId() == null) {
            throw new BusinessRuleException("An author is required");
        }
        if (request.categoryId() == null) {
            throw new BusinessRuleException("A category is required");
        }
        if (request.totalCopies() == null || request.totalCopies() < 1) {
            throw new BusinessRuleException("A new book needs at least one copy");
        }
        validatePublicationYear(request.publicationYear());
    }

    public void validateForUpdate(BookRequest request, Book existing) {
        validatePublicationYear(request.publicationYear());

        // Checked here as well as in Book.adjustTotalCopies, because the message
        // from this layer can name the book and the count, and the entity's is a
        // last-line guard for any path that skips this one.
        if (request.totalCopies() != null) {
            int out = existing.issuedCopies();
            if (request.totalCopies() < out) {
                throw new BusinessRuleException("Cannot reduce \"" + existing.getTitle()
                        + "\" to " + request.totalCopies() + " copies; " + out
                        + " are on loan or held");
            }
        }
    }

    /**
     * A publication year in the future, or before printing existed, is a typo.
     *
     * <p>Bounded above by next year rather than this one: a book published in
     * December is often catalogued with the following year on its title page.
     */
    private void validatePublicationYear(Integer year) {
        if (year == null) {
            return;
        }
        int nextYear = Year.now().getValue() + 1;
        if (year < EARLIEST_PUBLICATION_YEAR || year > nextYear) {
            throw new BusinessRuleException("Publication year must be between "
                    + EARLIEST_PUBLICATION_YEAR + " and " + nextYear);
        }
    }

    /**
     * Checks an ISBN's check digit.
     *
     * <p>The regex on the DTO confirms the shape; this confirms the number is
     * one that could actually have been issued. A transposed pair of digits
     * passes the pattern and fails here, which is the realistic error when
     * somebody types an ISBN off a copyright page.
     */
    public boolean isChecksumValid(String isbn) {
        String digits = isbn.replaceAll("[- ]", "").toUpperCase();
        if (digits.length() == 10) {
            return isbn10Valid(digits);
        }
        if (digits.length() == 13) {
            return isbn13Valid(digits);
        }
        return false;
    }

    private boolean isbn10Valid(String digits) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
            sum += (digits.charAt(i) - '0') * (10 - i);
        }
        char last = digits.charAt(9);
        // The final position may be X, which stands for ten.
        int check = last == 'X' ? 10 : (Character.isDigit(last) ? last - '0' : -1);
        if (check < 0) {
            return false;
        }
        return (sum + check) % 11 == 0;
    }

    private boolean isbn13Valid(String digits) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return false;
            }
            // Alternating weights of 1 and 3, starting at 1.
            sum += (digits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum % 10 == 0;
    }

    private void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
    }
}
