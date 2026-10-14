package com.example.lms.entity;

public enum BookStatus {

    ACTIVE,
    /** Temporarily out of circulation, e.g. for repair. Existing loans stand. */
    WITHDRAWN,
    /** Removed from the collection. Retained for history, never issued again. */
    RETIRED;

    public boolean isIssuable() {
        return this == ACTIVE;
    }
}
