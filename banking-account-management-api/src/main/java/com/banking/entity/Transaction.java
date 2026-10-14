package com.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One movement of money against one account.
 *
 * <h2>A transfer is two rows, not one</h2>
 * This is the central modelling decision. With one row per transfer carrying
 * {@code from} and {@code to}, the destination account has no row of its own:
 * its balance cannot be derived from {@code WHERE account_id = ?}, every
 * statement query becomes an OR across two columns, and the receiving customer's
 * history is missing the credit entirely. Verified — querying the destination
 * returns zero rows.
 *
 * <p>So a transfer writes a {@code TRANSFER_OUT} against the source and a
 * {@code TRANSFER_IN} against the destination, sharing one
 * {@code transactionReference}, in one database transaction. Two consequences
 * fall out of that:
 * <ul>
 *   <li>Any account's balance is {@code SUM(signed(amount))} over its own rows.</li>
 *   <li>A transfer nets to zero across the pair, so summing signed amounts over
 *       the <em>whole</em> ledger equals money that genuinely entered the bank.
 *       That is the system-wide reconciliation check, and it is not expressible
 *       in the one-row model.</li>
 * </ul>
 *
 * <h2>Append only</h2>
 * There is no update path and no delete path. A transaction that turns out to be
 * wrong is corrected by a reversing entry, never by editing history — the
 * correction is itself a fact somebody may need to see.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "ix_txn_account_date", columnList = "account_id, transacted_at"),
        @Index(name = "ix_txn_reference", columnList = "transaction_reference"),
        @Index(name = "ix_txn_type", columnList = "transaction_type"),
        @Index(name = "ix_txn_value_date", columnList = "value_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    /**
     * Shared by both halves of a transfer.
     *
     * <p>Not unique, precisely because two rows carry it. The pair is what makes
     * a transfer reconstructible from the ledger.
     */
    @Column(name = "transaction_reference", nullable = false, length = 32)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @ToString.Exclude
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    /** Always positive. The sign lives in {@link #direction}. */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * The balance immediately after this row was applied.
     *
     * <p>Denormalised on purpose. A statement has to show a running balance, and
     * recomputing it means summing every prior row for that account on every
     * read. Storing it also makes drift detectable: replaying the ledger and
     * comparing against this column locates the exact transaction where the two
     * diverged, rather than only proving that they have.
     */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    /** The other account, for transfers. Null for deposits and withdrawals. */
    @Column(name = "counterparty_account", length = 20)
    private String counterpartyAccount;

    @Column(name = "counterparty_name", length = 150)
    private String counterpartyName;

    @Column(length = 300)
    private String description;

    @Column(name = "transacted_at", nullable = false)
    @Builder.Default
    private Instant transactedAt = Instant.now();

    /**
     * The banking date this transaction counts against.
     *
     * <p>Separate from the instant. A deposit made at 23:55 belongs to that day's
     * statement; a transfer made after the cut-off may value-date to the next
     * working day. Monthly statements group on this, not on the timestamp,
     * because the customer's month and the server's UTC month are not the same
     * thing.
     */
    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "performed_by", length = 80)
    private String performedBy;

    public BigDecimal signedAmount() {
        return direction.signed(amount);
    }
}
