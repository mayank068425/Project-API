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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * A member's place in the queue for a title.
 *
 * <h2>Why a returned copy is held rather than shelved</h2>
 * The obvious implementation puts a returned copy straight back into general
 * availability. That starves the queue: the next member to reach the desk takes
 * it, the people who reserved it are still waiting, and no number of returns
 * ever reaches them. It is invisible in testing, because a test never has a
 * walk-in racing a queue.
 *
 * <p>So a return with a non-empty queue moves the copy into {@code heldCopies}
 * and marks the first reservation READY. The copy is in stock but not generally
 * issuable.
 *
 * <h2>Why the hold expires</h2>
 * A member who reserves and never collects would otherwise block that copy
 * permanently. On lapse the hold passes to the next in the queue, falling back
 * to general availability only when the queue is empty.
 */
@Entity
@Table(name = "reservation", indexes = {
        @Index(name = "ix_reservation_book_status", columnList = "book_id, status"),
        @Index(name = "ix_reservation_member", columnList = "member_id"),
        @Index(name = "ix_reservation_queue", columnList = "book_id, reserved_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    @ToString.Exclude
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @ToString.Exclude
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.WAITING;

    /** Queue position is by this timestamp, so the queue is strictly first-come. */
    @Column(name = "reserved_at", nullable = false)
    @Builder.Default
    private Instant reservedAt = Instant.now();

    @Column(name = "ready_at")
    private Instant readyAt;

    /** After this the held copy passes to the next member. */
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    /** After this the reservation lapses even if it never reached the front. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean notified = false;

    @Version
    private Long version;

    public boolean holdHasLapsed(Instant now) {
        return status == ReservationStatus.READY
                && holdExpiresAt != null && now.isAfter(holdExpiresAt);
    }

    public boolean hasAgedOut(Instant now) {
        return status == ReservationStatus.WAITING
                && expiresAt != null && now.isAfter(expiresAt);
    }

    public void markReady(Instant now, Instant holdUntil) {
        this.status = ReservationStatus.READY;
        this.readyAt = now;
        this.holdExpiresAt = holdUntil;
    }

    public void markFulfilled(Instant now) {
        this.status = ReservationStatus.FULFILLED;
        this.fulfilledAt = now;
    }
}
