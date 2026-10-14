package com.example.lms.entity;

public enum MembershipStatus {

    ACTIVE,
    /** Blocked by staff, e.g. for unpaid fines. Cannot borrow. */
    SUSPENDED,
    EXPIRED,
    CANCELLED;

    public boolean allowsBorrowing() {
        return this == ACTIVE;
    }
}
