package com.banking.repository;

import com.banking.entity.Transaction;
import com.banking.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select t from Transaction t join fetch t.account a join fetch a.customer "
            + "where t.id = :id")
    Optional<Transaction> findByIdWithAccount(@Param("id") Long id);

    /**
     * Both halves of a transfer.
     *
     * <p>Returns two rows for a transfer and one for anything else. The reference
     * is deliberately not unique — that is what makes a transfer reconstructible
     * from the ledger rather than requiring a separate transfers table.
     */
    @Query("select t from Transaction t join fetch t.account "
            + "where t.transactionReference = :reference order by t.direction desc")
    List<Transaction> findByReference(@Param("reference") String reference);

    @Query(value = "select t from Transaction t join fetch t.account a "
            + "where a.accountNumber = :accountNumber order by t.transactedAt desc",
            countQuery = "select count(t) from Transaction t "
                    + "where t.account.accountNumber = :accountNumber")
    Page<Transaction> findByAccountNumber(@Param("accountNumber") String accountNumber,
                                          Pageable pageable);

    @Query(value = "select t from Transaction t join fetch t.account a join fetch a.customer "
            + "where a.customer.id = :customerId order by t.transactedAt desc",
            countQuery = "select count(t) from Transaction t "
                    + "where t.account.customer.id = :customerId")
    Page<Transaction> findByCustomer(@Param("customerId") Long customerId, Pageable pageable);

    @Query(value = "select t from Transaction t join fetch t.account a join fetch a.customer",
            countQuery = "select count(t) from Transaction t")
    Page<Transaction> findAllWithAccount(Pageable pageable);

    /**
     * A monthly statement, bounded by value date.
     *
     * <p>Grouped on {@code valueDate} rather than the timestamp. A transaction at
     * 23:55 on the 31st belongs to that month's statement; grouping on a UTC
     * instant puts it in the next month for any customer east of Greenwich, and
     * the statement then disagrees with the one the customer expects.
     */
    @Query("select t from Transaction t join fetch t.account a "
            + "where a.accountNumber = :accountNumber "
            + "and t.valueDate between :from and :to order by t.valueDate, t.transactedAt")
    List<Transaction> findStatement(@Param("accountNumber") String accountNumber,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Mini statement: the most recent handful, newest first. */
    @Query("select t from Transaction t where t.account.accountNumber = :accountNumber "
            + "order by t.transactedAt desc")
    List<Transaction> findRecent(@Param("accountNumber") String accountNumber, Pageable pageable);

    /**
     * Total debited from an account today, for the daily limit check.
     *
     * <p>Sums outgoing transfers and withdrawals over the value date, so the
     * limit resets on the banking day rather than a rolling 24 hours — which is
     * what a customer means by "my daily limit".
     */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t "
            + "where t.account.id = :accountId and t.valueDate = :day "
            + "and t.direction = com.banking.entity.TransactionDirection.DEBIT "
            + "and t.transactionType in (com.banking.entity.TransactionType.WITHDRAWAL, "
            + "com.banking.entity.TransactionType.TRANSFER_OUT)")
    BigDecimal sumDebitsOn(@Param("accountId") Long accountId, @Param("day") LocalDate day);

    @Query("select coalesce(sum(case when t.direction = "
            + "com.banking.entity.TransactionDirection.CREDIT then t.amount else -t.amount end), 0) "
            + "from Transaction t where t.account.id = :accountId")
    BigDecimal ledgerBalance(@Param("accountId") Long accountId);

    long countByTransactionType(TransactionType type);

    @Query("select count(t) from Transaction t where t.transactedAt >= :since")
    long countSince(@Param("since") Instant since);

    /**
     * Transfers with an unmatched half.
     *
     * <p>Every TRANSFER_OUT must have a TRANSFER_IN under the same reference.
     * Impossible while both are written in one transaction, which is exactly why
     * it is worth checking: a row here means somebody made the two writes
     * non-atomic and money has been destroyed or created.
     */
    @Query("select t.transactionReference from Transaction t "
            + "where t.transactionType in (com.banking.entity.TransactionType.TRANSFER_OUT, "
            + "com.banking.entity.TransactionType.TRANSFER_IN) "
            + "group by t.transactionReference having count(t) <> 2")
    List<String> findUnbalancedTransfers();
}
