package com.banking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Every limit and window the bank actually tunes.
 *
 * <p>These change quarterly and appear in several places each — the transfer
 * path, the withdrawal path, the beneficiary check, the statement. As constants
 * they would require a rebuild, and one place would eventually be updated while
 * the others were not.
 */
@ConfigurationProperties(prefix = "banking.policy")
public class BankingPolicy {

    /**
     * The bank's own timezone.
     *
     * <p>Load-bearing. A transaction happens at an instant; a statement and a
     * daily limit are reckoned against a banking <em>date</em>. Converting in the
     * server's zone instead of the bank's moves the boundary by up to a day, so a
     * deposit at 23:55 lands on the wrong statement and a customer's daily limit
     * resets at the wrong moment.
     */
    private ZoneId zone = ZoneId.of("Asia/Kolkata");

    /**
     * How long after verification a beneficiary is restricted.
     *
     * <p>The anti-fraud control. Somebody who takes over a session adds a payee
     * and empties the account in seconds; this is the window in which the real
     * customer sees the notification and calls the bank.
     */
    private Duration beneficiaryCoolingPeriod = Duration.ofHours(4);

    /** The most that may be sent to a payee still inside that window. */
    private BigDecimal coolingPeriodTransferLimit = new BigDecimal("5000.00");

    /** Applied when the account carries no limit of its own. */
    private BigDecimal defaultDailyTransferLimit = new BigDecimal("200000.00");

    /** Single-transaction ceiling, regardless of the daily total. */
    private BigDecimal maxSingleTransferAmount = new BigDecimal("1000000.00");

    private BigDecimal maxCashDepositAmount = new BigDecimal("200000.00");
    private BigDecimal maxCashWithdrawalAmount = new BigDecimal("50000.00");

    private int maxBeneficiariesPerCustomer = 25;
    private int maxAccountsPerCustomer = 10;

    /** Minimum age to hold an unrestricted account. */
    private int minimumAccountHolderAge = 18;

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(ZoneId zone) {
        this.zone = zone;
    }

    public Duration getBeneficiaryCoolingPeriod() {
        return beneficiaryCoolingPeriod;
    }

    public void setBeneficiaryCoolingPeriod(Duration beneficiaryCoolingPeriod) {
        this.beneficiaryCoolingPeriod = beneficiaryCoolingPeriod;
    }

    public BigDecimal getCoolingPeriodTransferLimit() {
        return coolingPeriodTransferLimit;
    }

    public void setCoolingPeriodTransferLimit(BigDecimal coolingPeriodTransferLimit) {
        this.coolingPeriodTransferLimit = coolingPeriodTransferLimit;
    }

    public BigDecimal getDefaultDailyTransferLimit() {
        return defaultDailyTransferLimit;
    }

    public void setDefaultDailyTransferLimit(BigDecimal defaultDailyTransferLimit) {
        this.defaultDailyTransferLimit = defaultDailyTransferLimit;
    }

    public BigDecimal getMaxSingleTransferAmount() {
        return maxSingleTransferAmount;
    }

    public void setMaxSingleTransferAmount(BigDecimal maxSingleTransferAmount) {
        this.maxSingleTransferAmount = maxSingleTransferAmount;
    }

    public BigDecimal getMaxCashDepositAmount() {
        return maxCashDepositAmount;
    }

    public void setMaxCashDepositAmount(BigDecimal maxCashDepositAmount) {
        this.maxCashDepositAmount = maxCashDepositAmount;
    }

    public BigDecimal getMaxCashWithdrawalAmount() {
        return maxCashWithdrawalAmount;
    }

    public void setMaxCashWithdrawalAmount(BigDecimal maxCashWithdrawalAmount) {
        this.maxCashWithdrawalAmount = maxCashWithdrawalAmount;
    }

    public int getMaxBeneficiariesPerCustomer() {
        return maxBeneficiariesPerCustomer;
    }

    public void setMaxBeneficiariesPerCustomer(int maxBeneficiariesPerCustomer) {
        this.maxBeneficiariesPerCustomer = maxBeneficiariesPerCustomer;
    }

    public int getMaxAccountsPerCustomer() {
        return maxAccountsPerCustomer;
    }

    public void setMaxAccountsPerCustomer(int maxAccountsPerCustomer) {
        this.maxAccountsPerCustomer = maxAccountsPerCustomer;
    }

    public int getMinimumAccountHolderAge() {
        return minimumAccountHolderAge;
    }

    public void setMinimumAccountHolderAge(int minimumAccountHolderAge) {
        this.minimumAccountHolderAge = minimumAccountHolderAge;
    }
}
