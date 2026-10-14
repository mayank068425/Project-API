package com.example.lms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Every borrowing rule, bound from configuration.
 *
 * <p>These are the settings a library actually changes, and each appears in
 * several places: the issue check, the fine calculation, the overdue report, the
 * dashboard. Scattering them as literals guarantees one place is eventually
 * updated and the others are not, and the disagreement surfaces as a member
 * fined for a book the overdue report says is not yet due.
 */
@ConfigurationProperties(prefix = "lms.policy")
public class LibraryPolicy {

    /**
     * The library's own timezone. Load-bearing.
     *
     * <p>A due date is a calendar date; a return happens at an instant. Deciding
     * whether the return was late means converting between them, and the answer
     * depends on the zone. A member returning at 23:30 local on the due date is
     * on time — evaluated in UTC from an IST library, a return at 01:30 the next
     * morning reads as the previous day and is wrongly excused, while other
     * returns are wrongly fined. The two zones disagree for five and a half hours
     * of every twenty-four.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");

    private int loanDays = 14;
    private int maxBooksPerMember = 5;
    private BigDecimal finePerDay = new BigDecimal("2.00");

    /** Days after the due date before a fine starts. Zero matches the stated rule. */
    private int graceDays = 0;

    /**
     * Multiple of replacement cost at which the fine stops growing.
     *
     * <p>Uncapped, a book lost three years ago accrues more than the shelf it
     * came from. Past this point the charge is a lost-item charge, not a late
     * fee, and treating it as one produces a number no librarian will collect.
     */
    private BigDecimal fineCapMultiplier = new BigDecimal("1.00");

    private BigDecimal defaultReplacementCost = new BigDecimal("500.00");
    private int maxRenewals = 2;

    /** How long a returned copy is held for the first member in the queue. */
    private int reservationHoldDays = 2;

    /** How long a reservation stays in the queue before lapsing. */
    private int reservationExpiryDays = 30;

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(ZoneId zone) {
        this.zone = zone;
    }

    public int getLoanDays() {
        return loanDays;
    }

    public void setLoanDays(int loanDays) {
        this.loanDays = loanDays;
    }

    public int getMaxBooksPerMember() {
        return maxBooksPerMember;
    }

    public void setMaxBooksPerMember(int maxBooksPerMember) {
        this.maxBooksPerMember = maxBooksPerMember;
    }

    public BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public void setFinePerDay(BigDecimal finePerDay) {
        this.finePerDay = finePerDay;
    }

    public int getGraceDays() {
        return graceDays;
    }

    public void setGraceDays(int graceDays) {
        this.graceDays = graceDays;
    }

    public BigDecimal getFineCapMultiplier() {
        return fineCapMultiplier;
    }

    public void setFineCapMultiplier(BigDecimal fineCapMultiplier) {
        this.fineCapMultiplier = fineCapMultiplier;
    }

    public BigDecimal getDefaultReplacementCost() {
        return defaultReplacementCost;
    }

    public void setDefaultReplacementCost(BigDecimal defaultReplacementCost) {
        this.defaultReplacementCost = defaultReplacementCost;
    }

    public int getMaxRenewals() {
        return maxRenewals;
    }

    public void setMaxRenewals(int maxRenewals) {
        this.maxRenewals = maxRenewals;
    }

    public int getReservationHoldDays() {
        return reservationHoldDays;
    }

    public void setReservationHoldDays(int reservationHoldDays) {
        this.reservationHoldDays = reservationHoldDays;
    }

    public int getReservationExpiryDays() {
        return reservationExpiryDays;
    }

    public void setReservationExpiryDays(int reservationExpiryDays) {
        this.reservationExpiryDays = reservationExpiryDays;
    }
}
