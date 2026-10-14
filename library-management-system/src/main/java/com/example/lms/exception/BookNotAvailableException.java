package com.example.lms.exception;

/**
 * No copy of a title can be issued right now.
 *
 * <p>Carries the reason, because "not available" covers three situations that
 * need different actions at the desk: every copy is out (offer a reservation),
 * the remaining copies are held for people already in the queue (do not offer
 * one — they are behind those people), or the title is withdrawn from
 * circulation entirely (it is not coming back soon).
 */
public class BookNotAvailableException extends RuntimeException {

    public static final String CODE = "BOOK_NOT_AVAILABLE";

    public enum Reason {
        ALL_COPIES_ON_LOAN,
        RESERVED_FOR_QUEUE,
        TITLE_WITHDRAWN
    }

    private final Reason reason;

    public BookNotAvailableException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
