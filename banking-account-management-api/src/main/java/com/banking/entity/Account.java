package com.banking.entity;

import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A deposit account.
 *
 * <h2>Money representation</h2>
 * {@code DECIMAL(19,4)} and {@link BigDecimal} throughout. {@code double} cannot
 * represent 0.10 exactly, and a bank that cannot reconcile its ledger to the
 * cent has a regulatory problem rather than a rounding problem. Four stored
 * decimal places because interest accrual needs them; two are presented.
 *
 * <h2>The balance is a cached total</h2>
 * {@code balance} should always equal the signed sum of this account's ledger
 * rows. It is stored because every debit needs to read it under a lock and
 * summing the ledger on each transaction does not scale past a few thousand
 * rows. A stored derived value drifts, so a reconciliation query compares the two
 * — a balance nobody cross-checks is a balance that silently goes wrong.
 *
 * <h2>Mutation only ever happens under a row lock</h2>
 * Every method here that changes {@code balance} is called from a service that
 * loaded this row with {@code PESSIMISTIC_WRITE}. Without the lock, two
 * concurrent withdrawals both read the same balance, both pass the check, and
 * both write — verified: 1000, two withdrawals of 600, final balance 400.
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "ux_account_number", columnList = "account_number", unique = true),
        @Index(name = "ix_account_customer", columnList = "customer_id"),
        @Index(name = "ix_account_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    /** The number customers read aloud. Carries a Luhn check digit. */
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * The floor this account may not be taken below by a customer debit.
     *
     * <p>Copied from the account type at opening so it can be waived per account
     * without inventing a product. Explicitly ignored on the closure path — a
     * savings account with a 1000 floor could otherwise never reach the zero
     * balance that closing requires.
     */
    @Column(name = "minimum_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal minimumBalance = BigDecimal.ZERO;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "daily_transfer_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransferLimit;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Optimistic lock version.
     *
     * <p>Belt and braces beside the pessimistic lock: the row lock serialises the
     * transaction path, and this catches an employee editing the account through
     * the maintenance endpoint at the same moment.
     */
    @Version
    private Long version;

    /**
     * What the customer can actually spend.
     *
     * <p>Balance less the minimum balance, floored at zero. This is the figure
     * that belongs in an error message, because it is the one the customer can
     * act on — telling somebody their balance is 1000 when they cannot withdraw
     * any of it is not an answer.
     */
    public BigDecimal availableBalance() {
        return balance.subtract(minimumBalance).max(BigDecimal.ZERO);
    }

    public boolean isOperational() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Takes money out.
     *
     * <p>Called only with the row locked. The two failure modes are separated
     * because they need different advice: a balance that is genuinely too low
     * cannot be fixed by asking for less, and one blocked by the minimum balance
     * can.
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!status.canDebit()) {
            throw new InvalidTransactionException(
                    "Account " + accountNumber + " is " + status + " and cannot be debited",
                    InvalidTransactionException.Reason.SOURCE_NOT_ACTIVE);
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Balance is below the requested amount", availableBalance(), amount,
                    InsufficientBalanceException.Reason.BALANCE_TOO_LOW);
        }
        if (balance.subtract(amount).compareTo(minimumBalance) < 0) {
            throw new InsufficientBalanceException(
                    "This would take the account below its minimum balance of "
                            + minimumBalance, availableBalance(), amount,
                    InsufficientBalanceException.Reason.WOULD_BREACH_MINIMUM_BALANCE);
        }
        this.balance = this.balance.subtract(amount);
        this.lastTransactionAt = Instant.now();
    }

    /**
     * Puts money in.
     *
     * <p>Refused on a closed account: crediting one puts money somewhere nobody
     * can withdraw it from.
     */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        if (!status.canCredit()) {
            throw new InvalidTransactionException(
                    "Account " + accountNumber + " is " + status + " and cannot be credited",
                    InvalidTransactionException.Reason.DESTINATION_NOT_ACTIVE);
        }
        this.balance = this.balance.add(amount);
        this.lastTransactionAt = Instant.now();
    }

    /**
     * Withdraws the whole balance so the account can be closed.
     *
     * <p>The minimum balance is deliberately not applied. It exists to stop a
     * customer emptying an account they intend to keep, not to trap the money of
     * one they are closing.
     */
    public BigDecimal drainForClosure() {
        BigDecimal remaining = this.balance;
        this.balance = BigDecimal.ZERO;
        this.lastTransactionAt = Instant.now();
        return remaining;
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            // A negative debit is a credit by another name, and would bypass
            // every balance check above.
            throw new InvalidTransactionException("Amount must be greater than zero",
                    InvalidTransactionException.Reason.AMOUNT_NOT_POSITIVE);
        }
    }
}
