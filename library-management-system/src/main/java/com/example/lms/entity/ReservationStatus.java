package com.example.lms.entity;

public enum ReservationStatus {

    /** In the queue, waiting for a copy. */
    WAITING,
    /** A copy is set aside; the member has until holdExpiresAt to collect it. */
    READY,
    /** Collected: the reservation became a loan. */
    FULFILLED,
    CANCELLED,
    /** Lapsed uncollected, or aged out of the queue. */
    EXPIRED;

    public boolean isActive() {
        return this == WAITING || this == READY;
    }
}
