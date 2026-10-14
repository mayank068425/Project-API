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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One loan of one copy to one member.
 *
 * <h2>Dates versus instants</h2>
 * {@code issueDate} and {@code dueDate} are {@link LocalDate}: a library says a
 * book is due on the 24th, not at 14:32:07 on the 24th. The moment of return is
 * an instant and lives on {@link BookReturn}.
 *
 * <h2>Why the return is a separate entity</h2>
 * A return is an event with its own actor, timestamp and condition notes, not
 * three nullable columns bolted onto the loan. Separating them means a loan row
 * never carries half-filled return fields, and the return row can grow — damage
 * assessment, which desk took it — without widening the loan table.
 *
 * <p>The cost is one invariant that has to be enforced: exactly zero or one
 * return per loan. Two rows would mean the desk processed a return twice, which
 * puts the copy count up twice. A unique constraint on
 * {@code book_return.issue_id} is the guarantee; the status check below is what
 * produces a readable error before the constraint fires.
 */
@Entity
@Table(name = "book_issue", indexes = {
        @Index(name = "ux_issue_reference", columnList = "reference", unique = true),
        @Index(name = "ix_issue_member", columnList = "member_id"),
        @Index(name = "ix_issue_book", columnList = "book_id"),
        @Index(name = "ix_issue_status_due", columnList = "status, due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    @ToString.Exclude
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @ToString.Exclude
    private Member member;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /**
     * The return event, if the book has come back.
     *
     * <p>Mapped by the return side, which owns the foreign key. No cascade on
     * remove: deleting a loan must not silently delete the record that it came
     * back.
     */
    @OneToOne(mappedBy = "issue", fetch = FetchType.LAZY)
    @ToString.Exclude
    private BookReturn bookReturn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LoanStatus status = LoanStatus.ISSUED;

    @Column(name = "renewal_count", nullable = false)
    @Builder.Default
    private int renewalCount = 0;

    @Column(name = "issued_by", length = 80)
    private String issuedBy;

    @Column(length = 300)
    private String remarks;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public boolean isOutstanding() {
        return status.isOutstanding();
    }

    /**
     * Overdue as at a given library-local date.
     *
     * <p>The date is an argument rather than {@code LocalDate.now()}, so this
     * cannot accidentally evaluate in the server's timezone instead of the
     * library's.
     */
    public boolean isOverdueOn(LocalDate today) {
        return isOutstanding() && today.isAfter(dueDate);
    }

    public void markReturned() {
        this.status = LoanStatus.RETURNED;
    }
}
