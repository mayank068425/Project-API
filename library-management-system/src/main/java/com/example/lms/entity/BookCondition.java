package com.example.lms.entity;

public enum BookCondition {

    GOOD,
    /** Usable but marked. Recorded so the next borrower is not blamed for it. */
    MINOR_DAMAGE,
    /** Needs repair before it goes back on the shelf. */
    DAMAGED,
    LOST
}
