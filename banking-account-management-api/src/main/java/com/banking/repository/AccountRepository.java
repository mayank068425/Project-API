package com.banking.repository;

import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import com.banking.entity.AccountType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    @Query("select a from Account a join fetch a.customer where a.id = :id")
    Optional<Account> findByIdWithCustomer(@Param("id") Long id);

    @Query("select a from Account a join fetch a.customer where a.accountNumber = :number")
    Optional<Account> findByAccountNumberWithCustomer(@Param("number") String number);

    @Query(value = "select a from Account a join fetch a.customer where a.customer.id = :customerId",
            countQuery = "select count(a) from Account a where a.customer.id = :customerId")
    Page<Account> findByCustomer(@Param("customerId") Long customerId, Pageable pageable);

    List<Account> findByCustomerIdAndStatus(Long customerId, AccountStatus status);

    @Query(value = "select a from Account a join fetch a.customer",
            countQuery = "select count(a) from Account a")
    Page<Account> findAllWithCustomer(Pageable pageable);

    @Query(value = "select a from Account a join fetch a.customer where a.status = :status",
            countQuery = "select count(a) from Account a where a.status = :status")
    Page<Account> findByStatus(@Param("status") AccountStatus status, Pageable pageable);

    /**
     * Loads an account for update, taking a row lock.
     *
     * <p>The only permitted way to read an account on any path that moves money.
     * Without it, two concurrent withdrawals both read the same balance, both
     * pass the sufficiency check, and both write — reproduced: balance 1000, two
     * withdrawals of 600, final balance 400, nothing raised.
     *
     * <p>Optimistic locking alone detects that, but only after the work is done,
     * which turns every contended debit into a retry. The three second timeout
     * stops a stuck transaction parking the lock; the handler maps the timeout to
     * 429 so the caller retries rather than seeing a server error.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select a from Account a where a.accountNumber = :number")
    Optional<Account> lockByAccountNumber(@Param("number") String number);

    /**
     * Locks both sides of a transfer in a deterministic order.
     *
     * <p>{@code order by a.id} is the whole point. Two simultaneous transfers
     * between the same pair in opposite directions would otherwise take the locks
     * in opposite orders and deadlock — each holding what the other needs next.
     * MySQL kills one of them, and the failure only appears under real
     * concurrency, which is to say in production.
     *
     * <p>Sorting by primary key means every caller acquires the same pair in the
     * same sequence, so no cycle can be constructed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select a from Account a where a.accountNumber in :numbers order by a.id")
    List<Account> lockByAccountNumbersOrdered(@Param("numbers") List<String> numbers);

    long countByStatus(AccountStatus status);

    long countByAccountType(AccountType type);

    @Query("select coalesce(sum(a.balance), 0) from Account a "
            + "where a.status = com.banking.entity.AccountStatus.ACTIVE")
    BigDecimal totalDeposits();

    /**
     * Accounts whose stored balance disagrees with their own ledger.
     *
     * <p>Should always return nothing. The balance is a cached total of the
     * transaction rows, and a cached value with no cross-check drifts silently —
     * the first symptom is a customer disputing a statement that does not add up.
     * Returns [accountNumber, storedBalance, ledgerBalance] triples.
     */
    @Query("select a.accountNumber, a.balance, coalesce(sum("
            + "case when t.direction = com.banking.entity.TransactionDirection.CREDIT "
            + "then t.amount else -t.amount end), 0) "
            + "from Account a left join Transaction t on t.account = a "
            + "group by a.id, a.accountNumber, a.balance "
            + "having a.balance <> coalesce(sum("
            + "case when t.direction = com.banking.entity.TransactionDirection.CREDIT "
            + "then t.amount else -t.amount end), 0)")
    List<Object[]> findUnreconciledAccounts();

    /** Interest-bearing accounts, for the accrual job. */
    @Query("select a from Account a where a.status = com.banking.entity.AccountStatus.ACTIVE "
            + "and a.interestRate > 0 and a.balance > 0")
    List<Account> findInterestBearing();
}
