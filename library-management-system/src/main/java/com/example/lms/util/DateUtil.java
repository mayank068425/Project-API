package com.example.lms.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Conversions between instants and calendar dates.
 *
 * <h2>Why every method here demands a zone</h2>
 * A due date is a calendar date. A return happens at an instant. Deciding
 * whether the return was late means converting one into the other, and the
 * answer depends on which timezone you convert in.
 *
 * <p>A member handing a book back at 01:30 local time on the 11th is a day late
 * in the library's own zone — and 20:00 on the <em>10th</em> in UTC, which says
 * they were on time. The two disagree for five and a half hours out of every
 * twenty-four, so a library in IST running on a UTC server silently excuses late
 * returns and, at the other end of the day, fines punctual ones.
 *
 * <p>There is therefore no overload taking only an {@link Instant}. Calling
 * these without deciding on a zone is impossible by construction, which is the
 * only reliable way to stop it happening.
 */
public final class DateUtil {

    private DateUtil() {
    }

    public static LocalDate toLocalDate(Instant moment, ZoneId zone) {
        if (moment == null) {
            return null;
        }
        return moment.atZone(zone).toLocalDate();
    }

    public static LocalDate today(ZoneId zone) {
        return LocalDate.now(zone);
    }

    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Exclusive upper bound for a day, so range queries need no BETWEEN fudge. */
    public static Instant endOfDayExclusive(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    /**
     * Whole days from {@code from} to {@code to}, never negative.
     *
     * <p>The {@code max(0, ...)} is what stops an early return producing a
     * negative figure, and the plain subtraction is what makes returning
     * <em>on</em> the due date zero rather than one. Both of those are the
     * off-by-one that fines every punctual member.
     */
    public static long daysBetweenNonNegative(LocalDate from, LocalDate to) {
        return Math.max(0, ChronoUnit.DAYS.between(from, to));
    }

    public static boolean isAfterDate(LocalDate candidate, LocalDate reference) {
        return candidate.isAfter(reference);
    }
}
