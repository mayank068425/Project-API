package com.banking.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues account and customer numbers.
 *
 * <h2>Why not {@code MAX(account_number) + 1}</h2>
 * Two concurrent account openings both read the same maximum and both write the
 * number after it. The unique index means no bad row is stored — but one
 * perfectly ordinary opening fails with a constraint violation that the teller
 * cannot act on, and it happens exactly when the branch is busiest.
 *
 * <p>{@code UPDATE ... SET next_value = LAST_INSERT_ID(next_value + 1)} makes
 * the increment and the read a single atomic row update: the row lock is held
 * for the duration of one statement, and {@code LAST_INSERT_ID()} then returns
 * this session's value specifically.
 *
 * <h2>Why not a UUID</h2>
 * Customers read account numbers aloud over the phone and type them into
 * transfer forms. A sequential number with a check digit is the format the
 * domain actually uses.
 *
 * <h2>Why its own transaction</h2>
 * {@code REQUIRES_NEW}, so a number consumed by an opening that later rolls back
 * is skipped rather than reused. Gaps in the sequence are harmless; a reused
 * account number is not.
 */
@Component
public class AccountNumberGenerator {

    private final JdbcTemplate jdbc;

    public AccountNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextAccountNumber() {
        long value = nextValue(Constants.ACCOUNT_SEQUENCE);
        String base = String.format("%011d", value);
        return base + luhnCheckDigit(base);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextCustomerNumber() {
        return String.format("CUS%09d", nextValue(Constants.CUSTOMER_SEQUENCE));
    }

    private long nextValue(String sequenceName) {
        int updated = jdbc.update(
                "UPDATE number_sequence SET next_value = LAST_INSERT_ID(next_value + 1) "
                        + "WHERE sequence_name = ?", sequenceName);
        if (updated == 0) {
            throw new IllegalStateException("Sequence '" + sequenceName + "' is missing. "
                    + "Check that data.sql ran.");
        }
        Long value = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (value == null) {
            throw new IllegalStateException("Could not read the generated sequence value");
        }
        return value;
    }

    /**
     * Luhn check digit.
     *
     * <p>Catches every single-digit error and every adjacent transposition
     * except 09/90 — which is the realistic failure when somebody reads an
     * account number down the phone. It is not security; it is a typo filter
     * that turns "your transfer went to a stranger" into a validation message.
     */
    public static int luhnCheckDigit(String digits) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - (sum % 10)) % 10;
    }

    /** Whether a full account number's trailing check digit is consistent. */
    public static boolean isValid(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 2
                || !accountNumber.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String body = accountNumber.substring(0, accountNumber.length() - 1);
        int provided = accountNumber.charAt(accountNumber.length() - 1) - '0';
        return luhnCheckDigit(body) == provided;
    }
}
