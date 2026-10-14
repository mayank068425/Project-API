package com.banking.validation;

import com.banking.config.BankingPolicy;
import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import com.banking.entity.Customer;
import com.banking.exception.BusinessRuleException;
import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Account and transaction rules that need policy, the database, or both.
 */
@Component
public class AccountValidator {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final BankingPolicy policy;

    public AccountValidator(AccountRepository accounts, TransactionRepository transactions,
                            BankingPolicy policy) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.policy = policy;
    }

    /**
     * Whether this customer may open another account.
     *
     * <p>KYC gates opening rather than customer creation: a record can exist
     * while documents are checked, but no account is opened against it until they
     * pass.
     */
    public void validateForOpening(Customer customer) {
        if (!customer.isActive()) {
            throw new BusinessRuleException("Customer " + customer.getCustomerNumber()
                    + " is not active");
        }
        if (!customer.canOpenAccount()) {
            throw new BusinessRuleException("KYC for customer "
                    + customer.getCustomerNumber() + " is " + customer.getKycStatus()
                    + "; accounts cannot be opened until it is VERIFIED");
        }
        if (customer.getAge() < policy.getMinimumAccountHolderAge()) {
            // Age is derived from the date of birth on every read, so a customer
            // who was a minor at registration passes this the day they turn 18
            // without anybody running a job.
            throw new BusinessRuleException("Account holders must be at least "
                    + policy.getMinimumAccountHolderAge() + " years old");
        }

        long open = accounts.findByCustomerIdAndStatus(customer.getId(), AccountStatus.ACTIVE)
                .size();
        if (open >= policy.getMaxAccountsPerCustomer()) {
            throw new BusinessRuleException("Customer already holds "
                    + policy.getMaxAccountsPerCustomer() + " open accounts");
        }
    }

    /**
     * Whether an account can be closed.
     *
     * <p>Deliberately does not check the minimum balance. Closing pays out
     * whatever remains first, and applying the floor here would make a savings
     * account impossible to close.
     */
    public void validateForClosure(Account account) {
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BusinessRuleException("Account " + account.getAccountNumber()
                    + " is already closed");
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            // A frozen account is frozen for a reason, usually an investigation.
            // Closing it would move the money the freeze exists to hold.
            throw new BusinessRuleException("Account " + account.getAccountNumber()
                    + " is frozen and cannot be closed until it is unfrozen");
        }
        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Account " + account.getAccountNumber()
                    + " is overdrawn and must be settled before closing");
        }
    }

    /** Both sides of a transfer, checked before any money moves. */
    public void validateTransferPair(Account source, Account destination) {
        if (source.getId().equals(destination.getId())) {
            // Not harmless: it writes two ledger rows that net to zero and
            // inflates the statement with a movement that never happened.
            throw new InvalidTransactionException(
                    "The source and destination accounts are the same",
                    InvalidTransactionException.Reason.SAME_ACCOUNT);
        }
        if (!source.getStatus().canDebit()) {
            throw new InvalidTransactionException("Account " + source.getAccountNumber()
                    + " is " + source.getStatus() + " and cannot send money",
                    InvalidTransactionException.Reason.SOURCE_NOT_ACTIVE);
        }
        if (!destination.getStatus().canCredit()) {
            // A closed account must refuse credits too, or the money lands
            // somewhere nobody can withdraw it from.
            throw new InvalidTransactionException("Account "
                    + destination.getAccountNumber() + " is " + destination.getStatus()
                    + " and cannot receive money",
                    InvalidTransactionException.Reason.DESTINATION_NOT_ACTIVE);
        }
    }

    public void validateSingleTransferAmount(BigDecimal amount) {
        if (amount.compareTo(policy.getMaxSingleTransferAmount()) > 0) {
            throw new BusinessRuleException("Transfers above "
                    + policy.getMaxSingleTransferAmount()
                    + " must be arranged through a branch");
        }
    }

    /**
     * The daily debit limit.
     *
     * <p>Reckoned against the banking date in the bank's own timezone, not a
     * rolling 24 hours, because "my daily limit" means the calendar day to a
     * customer. The already-debited total is summed from the ledger rather than
     * cached, so it cannot drift.
     */
    public void validateDailyLimit(Account account, BigDecimal amount) {
        BigDecimal limit = account.getDailyTransferLimit() != null
                ? account.getDailyTransferLimit()
                : policy.getDefaultDailyTransferLimit();

        LocalDate today = LocalDate.now(policy.getZone());
        BigDecimal alreadyToday = transactions.sumDebitsOn(account.getId(), today);

        if (alreadyToday.add(amount).compareTo(limit) > 0) {
            BigDecimal remaining = limit.subtract(alreadyToday).max(BigDecimal.ZERO);
            throw new InsufficientBalanceException(
                    "This would exceed the daily limit of " + limit
                            + ". Remaining today: " + remaining,
                    remaining, amount,
                    InsufficientBalanceException.Reason.EXCEEDS_DAILY_LIMIT);
        }
    }

    public void validateCashDeposit(BigDecimal amount) {
        if (amount.compareTo(policy.getMaxCashDepositAmount()) > 0) {
            throw new BusinessRuleException("Cash deposits above "
                    + policy.getMaxCashDepositAmount() + " require additional reporting");
        }
    }

    public void validateCashWithdrawal(BigDecimal amount) {
        if (amount.compareTo(policy.getMaxCashWithdrawalAmount()) > 0) {
            throw new BusinessRuleException("Cash withdrawals above "
                    + policy.getMaxCashWithdrawalAmount() + " must be arranged in advance");
        }
    }
}
