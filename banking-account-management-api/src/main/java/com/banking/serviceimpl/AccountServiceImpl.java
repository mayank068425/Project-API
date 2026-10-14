package com.banking.serviceimpl;

import com.banking.config.BankingPolicy;
import com.banking.dto.request.AccountRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import com.banking.entity.AccountType;
import com.banking.entity.Customer;
import com.banking.entity.Transaction;
import com.banking.entity.TransactionDirection;
import com.banking.entity.TransactionType;
import com.banking.exception.BusinessRuleException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.AccountMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.security.SecurityUtils;
import com.banking.service.AccountService;
import com.banking.service.CustomerService;
import com.banking.util.AccountNumberGenerator;
import com.banking.util.Constants;
import com.banking.validation.AccountValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CustomerService customerService;
    private final AccountMapper mapper;
    private final AccountValidator validator;
    private final AccountNumberGenerator numbers;
    private final BankingPolicy policy;

    public AccountServiceImpl(AccountRepository accounts, TransactionRepository transactions,
                              CustomerService customerService, AccountMapper mapper,
                              AccountValidator validator, AccountNumberGenerator numbers,
                              BankingPolicy policy) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.customerService = customerService;
        this.mapper = mapper;
        this.validator = validator;
        this.numbers = numbers;
        this.policy = policy;
    }

    @Override
    @Transactional
    public AccountResponse open(AccountRequest request) {
        Customer customer = customerService.requireCustomer(request.customerId());
        validator.validateForOpening(customer);

        AccountType type;
        try {
            type = AccountType.valueOf(request.accountType().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Unknown account type: " + request.accountType());
        }

        Account account = Account.builder()
                .accountNumber(numbers.nextAccountNumber())
                .customer(customer)
                .accountType(type)
                // The product default, overridable per account. A bank waives the
                // floor for staff and legacy products, and a rule that cannot be
                // waived is one somebody works around with a fake account type.
                .minimumBalance(request.minimumBalance() != null
                        ? request.minimumBalance() : type.getDefaultMinimumBalance())
                .interestRate(type.getDefaultAnnualInterestRate())
                .dailyTransferLimit(request.dailyTransferLimit())
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .openedOn(LocalDate.now(policy.getZone()))
                .build();
        accounts.save(account);

        // The opening deposit is a real transaction, not a number written onto
        // the balance. Without the ledger row the account's first statement
        // starts with money that came from nowhere, and the reconciliation check
        // flags the account immediately.
        if (request.initialDeposit() != null
                && request.initialDeposit().compareTo(BigDecimal.ZERO) > 0) {
            account.credit(request.initialDeposit());
            transactions.save(Transaction.builder()
                    .transactionReference(newReference())
                    .account(account)
                    .transactionType(TransactionType.DEPOSIT)
                    .direction(TransactionDirection.CREDIT)
                    .amount(request.initialDeposit())
                    .balanceAfter(account.getBalance())
                    .description("Opening deposit")
                    .transactedAt(Instant.now())
                    .valueDate(LocalDate.now(policy.getZone()))
                    .performedBy(SecurityUtils.currentUsername())
                    .build());
            accounts.save(account);
        }

        log.info("opened {} account {} for customer {}", type, account.getAccountNumber(),
                customer.getCustomerNumber());
        return mapper.toResponse(account);
    }

    /**
     * Closes an account.
     *
     * <p>Any remaining balance is paid out as a real {@code CLOSURE_PAYOUT}
     * transaction before the account is closed. The minimum balance is
     * deliberately not applied to that payout: a savings account with a 1000
     * floor could otherwise never reach the zero balance closing requires, and
     * the customer's money would be trapped by a rule meant to protect it.
     */
    @Override
    @Transactional
    public AccountResponse close(Long id) {
        Account account = accounts.lockById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("account", id));
        validator.validateForClosure(account);

        BigDecimal remaining = account.getBalance();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            account.drainForClosure();
            transactions.save(Transaction.builder()
                    .transactionReference(newReference())
                    .account(account)
                    .transactionType(TransactionType.CLOSURE_PAYOUT)
                    .direction(TransactionDirection.DEBIT)
                    .amount(remaining)
                    .balanceAfter(account.getBalance())
                    .description("Balance paid out on account closure")
                    .transactedAt(Instant.now())
                    .valueDate(LocalDate.now(policy.getZone()))
                    .performedBy(SecurityUtils.currentUsername())
                    .build());
        }

        account.setStatus(AccountStatus.CLOSED);
        account.setClosedOn(LocalDate.now(policy.getZone()));
        accounts.save(account);

        log.info("closed account {} paying out {}", account.getAccountNumber(), remaining);
        return mapper.toResponse(reload(id));
    }

    /**
     * Freezes an account.
     *
     * <p>Blocks debits <em>and</em> credits. Blocking only debits would let money
     * keep arriving in an account under investigation, which is the opposite of
     * what a freeze is for.
     */
    @Override
    @Transactional
    public AccountResponse freeze(Long id, String reason) {
        SecurityUtils.requireStaff("Freezing an account");
        Account account = accounts.lockById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("account", id));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("A closed account cannot be frozen");
        }

        account.setStatus(AccountStatus.FROZEN);
        accounts.save(account);
        log.info("froze account {}{}", account.getAccountNumber(),
                reason == null ? "" : ": " + reason);
        return mapper.toResponse(reload(id));
    }

    @Override
    @Transactional
    public AccountResponse activate(Long id) {
        SecurityUtils.requireStaff("Activating an account");
        Account account = accounts.lockById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("account", id));

        if (account.getStatus() == AccountStatus.CLOSED) {
            // Closed is terminal. Reopening would resurrect an account number a
            // customer believes is dead, and the closure payout has already gone.
            throw new BusinessRuleException("A closed account cannot be reactivated. "
                    + "Open a new account instead.");
        }

        account.setStatus(AccountStatus.ACTIVE);
        accounts.save(account);
        return mapper.toResponse(reload(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        Account account = reload(id);
        SecurityUtils.requireSelfOrStaff(account.getCustomer().getId());
        return mapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getByAccountNumber(String accountNumber) {
        Account account = accounts.findByAccountNumberWithCustomer(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No account " + accountNumber));
        SecurityUtils.requireSelfOrStaff(account.getCustomer().getId());
        return mapper.toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String accountNumber) {
        Account account = accounts.findByAccountNumberWithCustomer(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No account " + accountNumber));
        SecurityUtils.requireSelfOrStaff(account.getCustomer().getId());
        return account.getBalance()
                .setScale(Constants.MONEY_SCALE, java.math.RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> list(AccountStatus status, Pageable pageable) {
        SecurityUtils.requireStaff("Listing all accounts");
        Page<Account> page = status == null
                ? accounts.findAllWithCustomer(pageable)
                : accounts.findByStatus(status, pageable);
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponse> byCustomer(Long customerId, Pageable pageable) {
        SecurityUtils.requireSelfOrStaff(customerId);
        return accounts.findByCustomer(customerId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Account requireAccount(String accountNumber) {
        return accounts.findByAccountNumberWithCustomer(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No account " + accountNumber));
    }

    private Account reload(Long id) {
        return accounts.findByIdWithCustomer(id)
                .orElseThrow(() -> ResourceNotFoundException.of("account", id));
    }

    private String newReference() {
        return Constants.TXN_REFERENCE_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
