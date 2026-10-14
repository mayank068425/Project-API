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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A charge raised against a member for a late return.
 *
 * <p>{@code DECIMAL(10,2)} and {@link BigDecimal}, never a floating point type.
 * A rate of 2.00 a day is exact in decimal and not in binary, and a library that
 * cannot reconcile its cash drawer against its fine ledger has a real problem
 * with an auditor.
 *
 * <h2>Why the workings are stored, not just the total</h2>
 * The question a member asks is not "how much" but "why". Answering it needs the
 * days, the rate, and the uncapped figure alongside the amount actually charged.
 * A row holding only the total makes every dispute unanswerable and sends the
 * librarian off to recompute it by hand.
 */
@Entity
@Table(name = "fine", indexes = {
        @Index(name = "ux_fine_issue", columnList = "issue_id", unique = true),
        @Index(name = "ix_fine_member_status", columnList = "member_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fine_id")
    private Long id;

    /**
     * One fine per loan, enforced by a unique index.
     *
     * <p>Without it, a return processed twice — a double-click, a retried
     * request — raises the same charge against the member twice.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false, unique = true)
    @ToString.Exclude
    private BookIssue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @ToString.Exclude
    private Member member;

    @Column(name = "days_overdue", nullable = false)
    private int daysOverdue;

    @Column(name = "rate_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerDay;

    /** Before the cap. Kept so the cap itself is explainable. */
    @Column(name = "calculated_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal calculatedAmount;

    /** What is actually owed. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean capped = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FineStatus status = FineStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "collected_by", length = 80)
    private String collectedBy;

    @Column(name = "waived_reason", length = 300)
    private String waivedReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public BigDecimal outstanding() {
        return amount.subtract(amountPaid);
    }
}
