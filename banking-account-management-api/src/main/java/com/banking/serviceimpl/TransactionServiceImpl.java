package com.banking.serviceimpl;

import com.banking.config.BankingPolicy;
import com.banking.dto.request.DepositRequest;
import com.banking.dto.request.TransferRequest;
import com.banking.dto.request.WithdrawRequest;
import com.banking.dto.response.StatementResponse;
import com.banking.dto.response.TransactionResponse;
import com.banking.dto.response.TransferResponse;
import com.banking.entity.Account;
import com.banking.entity.Beneficiary;
import com.banking.entity.Transaction;
import com.banking.entity.TransactionDirection;
import com.banking.entity.TransactionType;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.TransactionMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.security.SecurityUtils;
import com.banking.service.BeneficiaryService;
import com.banking.service.TransactionService;
import com.banking.util.Constants;
import com.banking.validation.AccountValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything that moves money.
 *
 * <h2>A transfer is two ledger rows</h2>
 * A debit against the source and a credit against the destination, sharing one
 * {@code transactionReference}, written in one database transaction. The
 * alternative — one row carrying {@code from} and {@code to} — leaves the
 * destination account with no row of its own, so its balance is not derivable
 * from {@code WHERE account_id = ?} and the receiving customer's statement is
 * missing the credit entirely.
 *
 * <p>The pair also nets to zero, which makes the system-wide check possible:
 * summing signed amounts across the whole ledger equals money that genuinely
 * entered the bank.
 *
 * <h2>Locking</h2>
 * Every account touched here is loaded with {@code PESSIMISTIC_WRITE}. Without
 * it two concurrent withdrawals both read the same balance, both pass the
 * sufficiency check and both write — reproduced: balance 1000, two withdrawals
 * of 600 each, final balance 400.
 *
 * <p>For a transfer both rows are locked <b>in primary-key order</b>, by
 * {@code lockByAccountNumbersOrdered}. Two simultaneous transfers between the
 * same pair in opposite directions would otherwise take the locks in opposite
 * orders and deadlock. Sorting means no cycle is constructible.
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TransactionMapper mapper;
    private final AccountValidator validator;
    private final BeneficiaryService beneficiaryService;
    private final BankingPolicy policy;

    public TransactionServiceImpl(AccountRepository accounts,
                                  TransactionRepository transactions,
                                  TransactionMapper mapper, AccountValidator validator,
                                  BeneficiaryService beneficiaryService,
                                  BankingPolicy policy) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.mapper = mapper;
        this.validator = validator;
        this.beneficiaryService = beneficiaryService;
        this.policy = policy;
    }

    // ------------------------------------------------------------- deposit

    @Override
    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        validator.validateCashDeposit(request.amount());

        Account account = accounts.lockByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account " + request.accountNumber()));

        account.credit(request.amount());

        Transaction entry = record(account, TransactionType.DEPOSIT,
                TransactionDirection.CREDIT, request.amount(), newReference(),
                null, null, description(request.description(), "Cash deposit"));

        accounts.save(account);
        log.info("deposit {} into {}", request.amount(), account.getAccountNumber());
        return mapper.toResponse(entry);
    }

    // ------------------------------------------------------------ withdraw

    @Override
    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request) {
        validator.validateCashWithdrawal(request.amount());

        Account account = accounts.lockByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account " + request.accountNumber()));

        validator.validateDailyLimit(account, request.amount());

        // Throws InsufficientBalanceException, distinguishing a balance that is
        // genuinely too low from one blocked by the minimum balance. The two need
        // different advice: only the second is fixable by asking for less.
        account.debit(request.amount());

        Transaction entry = record(account, TransactionType.WITHDRAWAL,
                TransactionDirection.DEBIT, request.amount(), newReference(),
                null, null, description(request.description(), "Cash withdrawal"));

        accounts.save(account);
        log.info("withdrawal {} from {}", request.amount(), account.getAccountNumber());
        return mapper.toResponse(entry);
    }

    // ------------------------------------------------------------ transfer

    /**
     * Moves money between two accounts.
     *
     * <p>{@code REPEATABLE_READ} explicitly rather than relying on the MySQL
     * default being the same. An isolation level changed at the server would
     * otherwise silently weaken every transfer in the system, and nothing would
     * fail visibly.
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransferResponse transfer(TransferRequest request) {
        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            // Checked before locking: locking a pair that is really one account
            // would take the same lock twice and the error would be obscure.
            throw new InvalidTransactionException(
                    "The source and destination accounts are the same",
                    InvalidTransactionException.Reason.SAME_ACCOUNT);
        }

        validator.validateSingleTransferAmount(request.amount());

        // Both rows, locked in primary-key order. This ordering is the deadlock
        // fix; see the class comment.
        List<Account> locked = accounts.lockByAccountNumbersOrdered(
                List.of(request.fromAccountNumber(), request.toAccountNumber()));

        if (locked.size() != 2) {
            // One of the two does not exist. Which one is deliberately not said:
            // naming it turns transfers into a probe for which account numbers
            // are real.
            throw new ResourceNotFoundException("One of the accounts could not be found");
        }

        Account source = locked.stream()
                .filter(a -> a.getAccountNumber().equals(request.fromAccountNumber()))
                .findFirst().orElseThrow();
        Account destination = locked.stream()
                .filter(a -> a.getAccountNumber().equals(request.toAccountNumber()))
                .findFirst().orElseThrow();

        // The caller must own the source. Staff move money on a customer's
        // behalf; a customer moves only their own.
        SecurityUtils.requireAccountOwnership(source.getCustomer().getId());

        validator.validateTransferPair(source, destination);
        validator.validateDailyLimit(source, request.amount());

        // Beneficiary rules are enforced here, on the path where money actually
        // moves, rather than only when the payee was saved. A customer sending to
        // an account they have not saved at all is allowed; one sending to a
        // saved payee is held to that payee's verification state.
        Optional<Beneficiary> payee = beneficiaryService.findForTransfer(
                source.getCustomer().getId(), request.toAccountNumber());
        payee.ifPresent(b -> beneficiaryService.assertTransferAllowed(b, request.amount()));

        String reference = newReference();
        String description = description(request.description(), "Fund transfer");

        source.debit(request.amount());
        destination.credit(request.amount());

        // Two rows, one reference, one transaction.
        record(source, TransactionType.TRANSFER_OUT, TransactionDirection.DEBIT,
                request.amount(), reference, destination.getAccountNumber(),
                destination.getCustomer().getFullName(), description);
        record(destination, TransactionType.TRANSFER_IN, TransactionDirection.CREDIT,
                request.amount(), reference, source.getAccountNumber(),
                source.getCustomer().getFullName(), description);

        accounts.save(source);
        accounts.save(destination);

        log.info("transfer {} from {} to {} ref {}", request.amount(),
                source.getAccountNumber(), destination.getAccountNumber(), reference);

        return new TransferResponse(reference, source.getAccountNumber(),
                destination.getAccountNumber(), destination.getCustomer().getFullName(),
                request.amount(), source.getBalance(), Instant.now(), description);
    }

    // -------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        Transaction entry = transactions.findByIdWithAccount(id)
                .orElseThrow(() -> ResourceNotFoundException.of("transaction", id));
        // Without this, any authenticated customer could walk the id space and
        // read strangers' transactions. The role check alone does not cover it.
        SecurityUtils.requireSelfOrStaff(entry.getAccount().getCustomer().getId());
        return mapper.toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> byReference(String reference) {
        List<Transaction> entries = transactions.findByReference(reference);
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("No transaction " + reference);
        }
        // A transfer has two rows belonging to two different customers. A payer
        // may see the pair; anybody else sees only their own side.
        if (!SecurityUtils.isStaff()) {
            Long own = SecurityUtils.currentCustomerId();
            entries = entries.stream()
                    .filter(t -> t.getAccount().getCustomer().getId().equals(own))
                    .toList();
            if (entries.isEmpty()) {
                throw new ResourceNotFoundException("No transaction " + reference);
            }
        }
        return entries.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(String accountNumber, Pageable pageable) {
        requireOwnership(accountNumber);
        return transactions.findByAccountNumber(accountNumber, pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> all(Pageable pageable) {
        if (SecurityUtils.isStaff()) {
            return transactions.findAllWithAccount(pageable).map(mapper::toResponse);
        }
        // A customer asking for "all transactions" means all of theirs.
        return transactions.findByCustomer(SecurityUtils.currentCustomerId(), pageable)
                .map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> miniStatement(String accountNumber) {
        requireOwnership(accountNumber);
        return transactions.findRecent(accountNumber,
                        PageRequest.of(0, Constants.MINI_STATEMENT_SIZE))
                .stream().map(mapper::toResponse).toList();
    }

    /**
     * A statement for a period.
     *
     * <p>The opening balance is derived by winding back from the earliest row in
     * the window using its stored {@code balanceAfter}, rather than summing every
     * transaction since the account opened. That keeps the cost proportional to
     * the window rather than to the account's whole history.
     */
    @Override
    @Transactional(readOnly = true)
    public StatementResponse statement(String accountNumber, LocalDate from, LocalDate to) {
        Account account = requireOwnership(accountNumber);

        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The start date is after the end date");
        }

        List<Transaction> entries = transactions.findStatement(accountNumber, from, to);

        BigDecimal opening;
        BigDecimal closing;
        if (entries.isEmpty()) {
            // No movement in the window, so the balance did not change.
            opening = account.getBalance();
            closing = account.getBalance();
        } else {
            Transaction first = entries.get(0);
            opening = first.getBalanceAfter().subtract(first.signedAmount());
            closing = entries.get(entries.size() - 1).getBalanceAfter();
        }

        BigDecimal credits = entries.stream()
                .filter(t -> t.getDirection() == TransactionDirection.CREDIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = entries.stream()
                .filter(t -> t.getDirection() == TransactionDirection.DEBIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StatementResponse(accountNumber,
                account.getCustomer().getFullName(), from, to,
                opening, closing, credits, debits, entries.size(),
                entries.stream().map(mapper::toResponse).toList());
    }

    /**
     * A calendar month, in the bank's timezone.
     *
     * <p>Bounded by value date rather than by timestamp. A transaction at 23:55
     * on the 31st belongs to that month; grouping on a UTC instant puts it in the
     * next month for any customer east of Greenwich, and the statement then
     * disagrees with the one they expect.
     */
    @Override
    @Transactional(readOnly = true)
    public StatementResponse monthlyStatement(String accountNumber, int year, int month) {
        YearMonth period = YearMonth.of(year, month);
        return statement(accountNumber, period.atDay(1), period.atEndOfMonth());
    }

    // ------------------------------------------------------------ internals

    /**
     * Writes one ledger row.
     *
     * <p>Called after the account balance has already been changed, so
     * {@code balanceAfter} is read from the account rather than recomputed. That
     * ordering matters: the stored running balance has to be the balance this row
     * produced, or a statement will not add up and the reconciliation check will
     * point at the wrong transaction.
     */
    private Transaction record(Account account, TransactionType type,
                               TransactionDirection direction, BigDecimal amount,
                               String reference, String counterpartyAccount,
                               String counterpartyName, String description) {
        Transaction entry = Transaction.builder()
                .transactionReference(reference)
                .account(account)
                .transactionType(type)
                .direction(direction)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .counterpartyAccount(counterpartyAccount)
                .counterpartyName(counterpartyName)
                .description(description)
                .transactedAt(Instant.now())
                // The banking date, in the bank's zone. Not derived from the
                // instant at read time, because the zone is configuration that
                // can change and an old transaction must not move months.
                .valueDate(LocalDate.now(policy.getZone()))
                .performedBy(SecurityUtils.currentUsername())
                .build();
        return transactions.save(entry);
    }

    private Account requireOwnership(String accountNumber) {
        Account account = accounts.findByAccountNumberWithCustomer(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account " + accountNumber));
        SecurityUtils.requireSelfOrStaff(account.getCustomer().getId());
        return account;
    }

    private String newReference() {
        return Constants.TXN_REFERENCE_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private String description(String supplied, String fallback) {
        return supplied == null || supplied.isBlank() ? fallback : supplied.strip();
    }

    /** Used by the account service when closing pays out a remaining balance. */
    List<Transaction> recordClosurePayout(Account account, BigDecimal amount) {
        List<Transaction> written = new ArrayList<>();
        written.add(record(account, TransactionType.CLOSURE_PAYOUT,
                TransactionDirection.DEBIT, amount, newReference(), null, null,
                "Balance paid out on account closure"));
        return written;
    }
}
