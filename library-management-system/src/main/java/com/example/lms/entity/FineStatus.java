package com.example.lms.entity;

public enum FineStatus {

    PENDING,
    PAID,
    /** Cleared by staff without payment. Requires a reason. */
    WAIVED;

    public boolean isOutstanding() {
        return this == PENDING;
    }
}
