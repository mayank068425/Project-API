package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The event of a copy coming back over the desk.
 *
 * <h2>Two timestamps, deliberately</h2>
 * {@code returnedAt} is the instant it happened — the audit fact.
 * {@code returnedOn} is the library-local calendar date of that instant, which
 * is what the fine was actually computed against.
 *
 * <p>Both are stored rather than one derived from the other, because the
 * conversion depends on the library's configured timezone and that is
 * configuration which can change. Recomputing an old return under a new zone
 * would silently restate whether a member was late three years ago, and there
 * would be no way to tell it had happened.
 */
@Entity
@Table(name = "book_return", indexes = {
        @Index(name = "ux_return_issue", columnList = "issue_id", unique = true),
        @Index(name = "ux_return_reference", columnList = "reference", unique = true),
        @Index(name = "ix_return_date", columnList = "returned_on")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    /**
     * The loan being closed. Unique, and that is the whole point.
     *
     * <p>One return per loan. Two rows would mean a double-click at the desk
     * raised the copy count twice and, if a fine were involved, charged the
     * member twice. The constraint makes that impossible rather than unlikely.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, unique = true)
    @ToString.Exclude
    private BookIssue issue;

    /** The moment the copy came back. */
    @Column(name = "returned_at", nullable = false)
    private Instant returnedAt;

    /** That instant as a date in the library's own timezone. */
    @Column(name = "returned_on", nullable = false)
    private LocalDate returnedOn;

    /** Days late as assessed at the time, kept so the figure is explainable later. */
    @Column(name = "days_late", nullable = false)
    @Builder.Default
    private int daysLate = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "book_condition", nullable = false, length = 20)
    @Builder.Default
    private BookCondition condition = BookCondition.GOOD;

    /**
     * Whether the copy went onto a reservation hold instead of the shelf.
     *
     * <p>Recorded because it changes what the librarian does with the physical
     * book: a held copy goes behind the desk, not back into the stacks.
     */
    @Column(name = "held_for_reservation", nullable = false)
    @Builder.Default
    private boolean heldForReservation = false;

    @Column(name = "received_by", length = 80)
    private String receivedBy;

    @Column(length = 300)
    private String remarks;

    public boolean wasLate() {
        return daysLate > 0;
    }
}
