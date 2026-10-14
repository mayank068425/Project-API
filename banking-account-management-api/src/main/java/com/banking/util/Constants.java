package com.banking.util;

import java.math.BigDecimal;

/**
 * Values referenced from more than one place.
 *
 * <p>Limits that a bank actually tunes — minimum balances, transfer ceilings,
 * cooling-off windows — are deliberately <em>not</em> here. Those are bound from
 * configuration in {@code BankingPolicy}, because a constant requires a rebuild
 * to change and product teams change these quarterly.
 */
public final class Constants {

    private Constants() {
    }

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_HEADER = "Authorization";

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_CUSTOMER_ID = "customerId";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_BANK_EMPLOYEE = "BANK_EMPLOYEE";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int ACCOUNT_LOCK_MINUTES = 15;
    public static final int PASSWORD_MIN_LENGTH = 12;

    /**
     * Money scale. Two decimal places, half-up.
     *
     * <p>Every monetary column is {@code DECIMAL(19,4)} and every field is
     * {@link BigDecimal}. Interest calculations need the extra places; amounts
     * presented to a customer are rounded to this scale at the boundary, once,
     * rather than repeatedly along the way.
     */
    public static final int MONEY_SCALE = 2;
    public static final int STORAGE_SCALE = 4;
    public static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(MONEY_SCALE);

    public static final String ACCOUNT_SEQUENCE = "ACCOUNT";
    public static final String CUSTOMER_SEQUENCE = "CUSTOMER";
    public static final String TXN_REFERENCE_PREFIX = "TXN";

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MINI_STATEMENT_SIZE = 10;
}
