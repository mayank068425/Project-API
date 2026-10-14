package com.example.lms.util;

/**
 * Values referenced from more than one place.
 *
 * <p>Only genuinely shared constants live here. Borrowing rules — loan period,
 * fine rate, limits — are deliberately <em>not</em> in this class: a library
 * changes those, and a constant requires a rebuild to change. They are bound
 * from configuration in {@code LibraryPolicy} instead.
 */
public final class Constants {

    private Constants() {
    }

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_HEADER = "Authorization";

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_MEMBER_ID = "memberId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_LIBRARIAN = "LIBRARIAN";
    public static final String ROLE_MEMBER = "MEMBER";

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int ACCOUNT_LOCK_MINUTES = 15;
    public static final int PASSWORD_MIN_LENGTH = 12;

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final String MEMBER_NUMBER_FORMAT = "LIB%07d";
    public static final String ISSUE_REFERENCE_PREFIX = "ISS-";
    public static final String RETURN_REFERENCE_PREFIX = "RET-";
}
