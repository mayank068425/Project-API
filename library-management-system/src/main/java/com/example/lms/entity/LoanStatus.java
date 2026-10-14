package com.example.lms.entity;

public enum LoanStatus {

    ISSUED,
    RETURNED,
    /** Still out, past the due date. A reporting label, set by the sweep. */
    OVERDUE,
    /** Written off; the fine has reached the replacement cost. */
    LOST;

    public boolean isOutstanding() {
        return this == ISSUED || this == OVERDUE;
    }
}
