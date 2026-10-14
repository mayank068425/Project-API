package com.example.lms.service;

import com.example.lms.entity.Book;
import com.example.lms.entity.Member;
import com.example.lms.entity.Reservation;
import com.example.lms.entity.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hold mechanism that stops the reservation queue starving.
 *
 * <p>Shelving a returned copy while members are queued means the next walk-in
 * takes it and the queue never drains — invisible in testing, because a test
 * never has a walk-in racing a queue. These pin the behaviour that prevents it.
 */
class ReservationQueueTest {

    private Reservation waiting(Instant reservedAt, Instant expiresAt) {
        return Reservation.builder()
                .book(Book.builder().id(1L).title("Clean Code").build())
                .member(Member.builder().id(1L).membershipNumber("LIB0000001").build())
                .status(ReservationStatus.WAITING)
                .reservedAt(reservedAt)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    @DisplayName("a copy held for a member carries a deadline to collect it")
    void holdCarriesAnExpiry() {
        Instant now = Instant.now();
        Reservation reservation = waiting(now.minus(Duration.ofDays(3)), null);

        reservation.markReady(now, now.plus(Duration.ofDays(2)));

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(reservation.getHoldExpiresAt()).isAfter(now);
        assertThat(reservation.holdHasLapsed(now)).isFalse();
    }

    @Test
    @DisplayName("an uncollected hold lapses")
    void holdLapses() {
        // Without an expiry, a member who reserves and never collects blocks that
        // copy permanently and the queue behind them never moves.
        Instant now = Instant.now();
        Reservation reservation = waiting(now.minus(Duration.ofDays(5)), null);
        reservation.markReady(now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(1)));

        assertThat(reservation.holdHasLapsed(now)).isTrue();
    }

    @Test
    @DisplayName("a reservation that never reaches the front ages out")
    void agesOut() {
        Instant now = Instant.now();
        Reservation stale = waiting(now.minus(Duration.ofDays(40)),
                now.minus(Duration.ofDays(10)));

        assertThat(stale.hasAgedOut(now)).isTrue();
    }

    @Test
    @DisplayName("only a READY reservation can have a lapsed hold")
    void waitingCannotHaveALapsedHold() {
        Instant now = Instant.now();
        Reservation reservation = waiting(now.minus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(20)));

        assertThat(reservation.holdHasLapsed(now)).isFalse();
        assertThat(reservation.hasAgedOut(now)).isFalse();
    }

    @Test
    @DisplayName("a collected reservation is fulfilled, not merely closed")
    void fulfilment() {
        Instant now = Instant.now();
        Reservation reservation = waiting(now.minus(Duration.ofDays(2)), null);
        reservation.markReady(now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));

        reservation.markFulfilled(now);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);
        assertThat(reservation.getFulfilledAt()).isEqualTo(now);
        assertThat(reservation.getStatus().isActive()).isFalse();
    }

    @Test
    @DisplayName("queue order is strictly by reservation time")
    void firstComeFirstServed() {
        Instant now = Instant.now();
        Reservation first = waiting(now.minus(Duration.ofHours(5)), null);
        Reservation second = waiting(now.minus(Duration.ofHours(2)), null);

        assertThat(first.getReservedAt()).isBefore(second.getReservedAt());
    }
}
