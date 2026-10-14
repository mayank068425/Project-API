package com.example.lms.service;

import com.example.lms.entity.Author;
import com.example.lms.entity.Book;
import com.example.lms.entity.BookStatus;
import com.example.lms.entity.Category;
import com.example.lms.exception.BookNotAvailableException;
import com.example.lms.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The three copy counters and the invariant between them.
 *
 * <p>These are unit tests of the arithmetic. The pessimistic lock that makes it
 * safe under concurrency is a database behaviour and needs Testcontainers, which
 * the README flags as the most valuable missing test.
 */
class BookCopyCountTest {

    private Book book(int total, int available, int held, BookStatus status) {
        return Book.builder()
                .id(1L).isbn("9780132350884").title("Clean Code")
                .author(Author.builder().id(1L).name("Robert C. Martin").build())
                .category(Category.builder().id(1L).name("Programming").build())
                .totalCopies(total).availableCopies(available).heldCopies(held)
                .status(status).build();
    }

    private Book active(int total, int available) {
        return book(total, available, 0, BookStatus.ACTIVE);
    }

    @Test
    @DisplayName("available + held + on loan always equals total")
    void invariantHolds() {
        Book book = active(5, 5);
        book.takeCopy();
        book.takeCopy();
        book.holdCopy();

        assertThat(book.getAvailableCopies() + book.getHeldCopies() + book.issuedCopies())
                .isEqualTo(book.getTotalCopies());
    }

    @Test
    @DisplayName("taking a copy when none are free names all-copies-on-loan")
    void refusesWhenNoneAvailable() {
        assertThatThrownBy(() -> active(3, 0).takeCopy())
                .isInstanceOf(BookNotAvailableException.class)
                .extracting(ex -> ((BookNotAvailableException) ex).getReason())
                .isEqualTo(BookNotAvailableException.Reason.ALL_COPIES_ON_LOAN);
    }

    @Test
    @DisplayName("a copy held for the queue names reserved-for-queue instead")
    void distinguishesHeldFromUnavailable() {
        // The two need different responses at the desk: offering a reservation to
        // someone behind an existing queue is not useful advice.
        assertThatThrownBy(() -> book(3, 0, 1, BookStatus.ACTIVE).takeCopy())
                .isInstanceOf(BookNotAvailableException.class)
                .extracting(ex -> ((BookNotAvailableException) ex).getReason())
                .isEqualTo(BookNotAvailableException.Reason.RESERVED_FOR_QUEUE);
    }

    @Test
    @DisplayName("a withdrawn title is refused even with copies on the shelf")
    void refusesWithdrawnTitle() {
        assertThatThrownBy(() -> book(3, 3, 0, BookStatus.WITHDRAWN).takeCopy())
                .isInstanceOf(BookNotAvailableException.class)
                .extracting(ex -> ((BookNotAvailableException) ex).getReason())
                .isEqualTo(BookNotAvailableException.Reason.TITLE_WITHDRAWN);
    }

    @Test
    @DisplayName("holding takes a copy out of general availability without losing it")
    void holdingRemovesFromAvailable() {
        Book book = active(3, 2);
        book.holdCopy();

        assertThat(book.getAvailableCopies()).isEqualTo(1);
        assertThat(book.getHeldCopies()).isEqualTo(1);
        assertThat(book.getTotalCopies()).isEqualTo(3);
    }

    @Test
    @DisplayName("collecting a hold turns it into a loan")
    void collectingAHold() {
        Book book = active(3, 2);
        book.holdCopy();
        book.collectHeldCopy();

        assertThat(book.getHeldCopies()).isZero();
        assertThat(book.getAvailableCopies()).isEqualTo(1);
        assertThat(book.issuedCopies()).isEqualTo(2);
    }

    @Test
    @DisplayName("a lapsed hold returns the copy to the shelf")
    void releasingAHold() {
        Book book = active(3, 2);
        book.holdCopy();
        book.releaseHold();

        assertThat(book.getHeldCopies()).isZero();
        assertThat(book.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    @DisplayName("adding stock makes it immediately borrowable")
    void addingStock() {
        Book book = active(2, 0);
        book.adjustTotalCopies(5);

        assertThat(book.getTotalCopies()).isEqualTo(5);
        assertThat(book.getAvailableCopies()).isEqualTo(3);
    }

    @Test
    @DisplayName("stock cannot be cut below what is out on loan")
    void cannotCutStockBelowLoans() {
        Book book = active(5, 1);

        assertThatThrownBy(() -> book.adjustTotalCopies(2))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("on loan");
        assertThat(book.getTotalCopies()).isEqualTo(5);
    }
}
