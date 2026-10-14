package com.example.lms.entity;

import com.example.lms.exception.BookNotAvailableException;
import com.example.lms.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A title held by the library.
 *
 * <h2>The three copy counters</h2>
 * {@code totalCopies} is physical stock. {@code availableCopies} is what a
 * walk-in can borrow now. {@code heldCopies} is stock set aside for members at
 * the front of the reservation queue.
 *
 * <p>Availability is <em>derived</em>: it should always equal
 * {@code totalCopies - activeLoans - heldCopies}. It is stored anyway, because
 * "show me available books" is the most frequent query in the system and
 * counting loans on every search does not scale.
 *
 * <p>A stored derived value drifts, so two things guard it: every mutation
 * happens under a row lock, and a reconciliation report compares the counters
 * against the loan table. A counter with no cross-check fails silently, and the
 * first symptom is a member sent to an empty shelf.
 */
@Entity
@Table(name = "book", indexes = {
        @Index(name = "ux_book_isbn", columnList = "isbn", unique = true),
        @Index(name = "ix_book_title", columnList = "title"),
        @Index(name = "ix_book_category", columnList = "category_id"),
        @Index(name = "ix_book_author", columnList = "author_id"),
        @Index(name = "ix_book_available", columnList = "available_copies")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @Column(nullable = false, length = 300)
    private String title;

    /**
     * LAZY, although the default for {@code @ManyToOne} is EAGER.
     *
     * <p>Left eager, listing books loads the author and the category for every
     * row whether the caller wants them or not. The endpoints that need those
     * names use a join fetch instead, which is one query rather than two per row.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    @ToString.Exclude
    private Author author;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    @ToString.Exclude
    private Category category;

    @Column(length = 200)
    private String publisher;

    @Column(length = 40)
    private String edition;

    @Column(length = 40)
    @Builder.Default
    private String language = "English";

    @Column(name = "shelf_number", length = 30)
    private String shelfNumber;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "total_copies", nullable = false)
    @Builder.Default
    private int totalCopies = 1;

    @Column(name = "available_copies", nullable = false)
    @Builder.Default
    private int availableCopies = 1;

    @Column(name = "held_copies", nullable = false)
    @Builder.Default
    private int heldCopies = 0;

    /**
     * What the library would pay to replace this copy.
     *
     * <p>Used as the ceiling on a fine. Nullable, with a policy fallback, because
     * older stock frequently has no recorded cost.
     */
    @Column(name = "replacement_cost", precision = 10, scale = 2)
    private BigDecimal replacementCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookStatus status = BookStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Optimistic lock version.
     *
     * <p>Belt and braces alongside the pessimistic lock on the issue path: the
     * row lock serialises issue and return, and this catches a librarian editing
     * stock through the catalogue endpoint at the same moment.
     */
    @Version
    private Long version;

    public int issuedCopies() {
        return totalCopies - availableCopies - heldCopies;
    }

    public boolean isAvailableForLoan() {
        return status.isIssuable() && availableCopies > 0;
    }

    /**
     * Takes one copy off the shelf.
     *
     * <p>Only ever called with the row locked. Without the lock, two concurrent
     * issues of the last copy both read {@code availableCopies == 1}, both pass
     * this check, and both decrement — leaving the counter at zero with two loans
     * outstanding against one physical copy, and no error anywhere.
     */
    public void takeCopy() {
        if (!status.isIssuable()) {
            throw new BookNotAvailableException(
                    "\"" + title + "\" is " + status + " and cannot be issued",
                    BookNotAvailableException.Reason.TITLE_WITHDRAWN);
        }
        if (availableCopies <= 0) {
            // Which of the two reasons applies changes what the desk should say,
            // so they are distinguished rather than collapsed into "unavailable".
            throw new BookNotAvailableException(
                    heldCopies > 0
                            ? "The remaining copies of \"" + title + "\" are held for members "
                                    + "in the reservation queue"
                            : "No copies of \"" + title + "\" are available",
                    heldCopies > 0
                            ? BookNotAvailableException.Reason.RESERVED_FOR_QUEUE
                            : BookNotAvailableException.Reason.ALL_COPIES_ON_LOAN);
        }
        this.availableCopies--;
    }

    /**
     * Puts a copy back into stock.
     *
     * <p>Note what this does <em>not</em> decide: whether the copy becomes
     * generally available. When members are queued for the title the copy is held
     * for the first of them instead, and releasing it here would let the next
     * walk-in take it while the queue never drains. That decision needs to see
     * the queue, so it lives in the return flow.
     */
    public void returnCopy() {
        this.availableCopies++;
    }

    /** Moves a copy from general availability into a reservation hold. */
    public void holdCopy() {
        if (availableCopies <= 0) {
            throw new BusinessRuleException("No copy of \"" + title + "\" is free to hold");
        }
        this.availableCopies--;
        this.heldCopies++;
    }

    /** A held copy is collected by the member it was held for. */
    public void collectHeldCopy() {
        if (heldCopies <= 0) {
            throw new BusinessRuleException("No copy of \"" + title + "\" is being held");
        }
        this.heldCopies--;
    }

    /** A hold lapsed uncollected; the copy returns to the shelf. */
    public void releaseHold() {
        if (heldCopies <= 0) {
            throw new BusinessRuleException("No copy of \"" + title + "\" is being held");
        }
        this.heldCopies--;
        this.availableCopies++;
    }

    /**
     * Adjusts physical stock, refusing to cut below what is out.
     *
     * <p>Available copies move by the same delta, so added stock is immediately
     * borrowable and removed stock comes off the shelf rather than out of
     * somebody's active loan.
     */
    public void adjustTotalCopies(int newTotal) {
        if (newTotal < 0) {
            throw new IllegalArgumentException("Total copies cannot be negative");
        }
        int delta = newTotal - this.totalCopies;
        if (this.availableCopies + delta < 0) {
            throw new BusinessRuleException("Cannot reduce stock to " + newTotal + "; "
                    + issuedCopies() + " copies are on loan or held");
        }
        this.totalCopies = newTotal;
        this.availableCopies += delta;
    }

    public BigDecimal effectiveReplacementCost(BigDecimal fallback) {
        return replacementCost != null ? replacementCost : fallback;
    }
}
