package com.example.lms.service;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.util.DateUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Works out what a late return costs.
 *
 * <p>A concrete component rather than an interface with an implementation: it is
 * a pure calculation with no collaborators to substitute, and an interface over
 * it would be indirection with nothing behind it.
 *
 * <h2>The rule</h2>
 * Days late times the daily rate, less any grace period, capped at the book's
 * replacement cost. Worked through: due on the 10th, returned the 15th, at 2.00 a
 * day gives 5 days and a fine of 10.00.
 *
 * <h2>Where it goes wrong</h2>
 * <b>Off by one.</b> A book returned <em>on</em> its due date is not late. Using
 * {@code >=} instead of {@code >}, or dropping the {@code max(0, ...)}, fines
 * every punctual member by exactly one day.
 *
 * <p><b>The timezone.</b> A due date is a calendar date and a return is an
 * instant, so the comparison needs a zone, and the wrong one moves the boundary
 * by up to a day. See {@link DateUtil} — the conversion is only available there,
 * and only with an explicit zone.
 *
 * <p><b>The cap.</b> Uncapped, a book lost three years ago accrues a fine larger
 * than the shelf it came from.
 */
@Component
public class FineCalculator {

    private final LibraryPolicy policy;

    public FineCalculator(LibraryPolicy policy) {
        this.policy = policy;
    }

    /**
     * @param calculatedAmount before the cap
     * @param amount           after the cap; what is charged
     * @param capped           whether the cap bound, which changes what the desk
     *                         should tell the member
     */
    public record Assessment(LocalDate dueDate, LocalDate returnedOn, long rawDaysLate,
                             int chargeableDays, int graceDays, BigDecimal ratePerDay,
                             BigDecimal calculatedAmount, BigDecimal cap, BigDecimal amount,
                             boolean capped) {

        public boolean isChargeable() {
            return amount.compareTo(BigDecimal.ZERO) > 0;
        }

        /** A one-line explanation, suitable for reading out at the desk. */
        public String explain() {
            if (!isChargeable()) {
                return rawDaysLate <= 0
                        ? "Returned on or before the due date. No fine."
                        : rawDaysLate + " day(s) late, within the " + graceDays
                                + " day grace period. No fine.";
            }
            String base = chargeableDays + " day(s) late at " + ratePerDay + " per day = "
                    + calculatedAmount;
            return capped
                    ? base + ", capped at the replacement cost of " + cap + "."
                    : base + ".";
        }
    }

    public LocalDate today() {
        return DateUtil.today(policy.getZone());
    }

    public Assessment assess(LocalDate dueDate, LocalDate returnedOn,
                             BigDecimal replacementCost) {
        if (dueDate == null || returnedOn == null) {
            throw new IllegalArgumentException("Both the due date and the return date are required");
        }

        // daysBetweenNonNegative floors at zero, which is what makes an early
        // return produce no fine rather than a negative one.
        long rawDaysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDate, returnedOn);
        int chargeableDays = (int) Math.max(0, rawDaysLate - policy.getGraceDays());

        BigDecimal rate = policy.getFinePerDay();
        BigDecimal calculated = rate.multiply(BigDecimal.valueOf(chargeableDays))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal cost = replacementCost != null
                ? replacementCost : policy.getDefaultReplacementCost();
        BigDecimal cap = cost.multiply(policy.getFineCapMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        boolean capped = calculated.compareTo(cap) > 0;

        return new Assessment(dueDate, returnedOn, rawDaysLate, chargeableDays,
                policy.getGraceDays(), rate, calculated, cap,
                capped ? cap : calculated, capped);
    }

    /** As at today, for a loan that is still out. Grows daily until it comes back. */
    public Assessment assessOutstanding(LocalDate dueDate, BigDecimal replacementCost) {
        return assess(dueDate, today(), replacementCost);
    }
}
