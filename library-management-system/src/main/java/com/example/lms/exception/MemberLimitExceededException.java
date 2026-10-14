package com.example.lms.exception;

/**
 * The member is holding as many books as they are allowed.
 *
 * <p>Carries the current count and the limit so the desk can say "you have five
 * out, the limit is five" rather than a bare refusal the member cannot act on.
 */
public class MemberLimitExceededException extends RuntimeException {

    public static final String CODE = "MEMBER_LIMIT_EXCEEDED";

    private final int currentCount;
    private final int limit;

    public MemberLimitExceededException(String membershipNumber, int currentCount, int limit) {
        super("Member " + membershipNumber + " already has " + currentCount
                + " book(s) on loan; the limit is " + limit);
        this.currentCount = currentCount;
        this.limit = limit;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getLimit() {
        return limit;
    }
}
