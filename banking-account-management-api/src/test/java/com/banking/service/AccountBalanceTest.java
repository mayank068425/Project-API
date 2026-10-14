package com.banking.service;

import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import com.banking.entity.AccountType;
import com.banking.entity.Customer;
import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The balance rules, in isolation.
 *
 * <p>These test the arithmetic and the refusals. The pessimistic locks that make
 * them safe under concurrency are a database behaviour that a unit test cannot
 * exercise — that needs Testcontainers, and the README flags it as the most
 * valuable missing test.
 */
class AccountBalanceTest {

    private Account account(AccountType type, String balance, String minimum,
                            AccountStatus status) {
        return Account.builder()
                .id(1L).accountNumber("000001000017")
                .customer(Customer.builder().id(1L).firstName("Priya").lastName("Nair").build())
                .accountType(type)
                .status(status)
                .balance(new BigDecimal(balance))
                .minimumBalance(new BigDecimal(minimum))
                .openedOn(LocalDate.now())
                .build();
    }

    private Account savings(String balance) {
        return account(AccountType.SAVINGS, balance, "1000.00", AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("available balance is the balance less the minimum, never negative")
    void availableBalance() {
        assertThat(savings("25000.00").availableBalance()).isEqualByComparingTo("24000.00");
        // A balance already at the floor leaves nothing spendable, not a negative
        // number that would confuse every caller downstream.
        assertThat(savings("1000.00").availableBalance()).isEqualByComparingTo("0.00");
        assertThat(savings("500.00").availableBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a debit within the available balance succeeds")
    void debitSucceeds() {
        Account account = savings("25000.00");
        account.debit(new BigDecimal("5000.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("20000.00");
        assertThat(account.getLastTransactionAt()).isNotNull();
    }

    @Test
    @DisplayName("a debit larger than the balance is refused as BALANCE_TOO_LOW")
    void refusesBalanceTooLow() {
        assertThatThrownBy(() -> savings("500.00").debit(new BigDecimal("900.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .extracting(ex -> ((InsufficientBalanceException) ex).getReason())
                .isEqualTo(InsufficientBalanceException.Reason.BALANCE_TOO_LOW);
    }

    @Test
    @DisplayName("a debit breaching the minimum is a different, fixable refusal")
    void refusesMinimumBalanceBreach() {
        // The two are separated because they need different advice: this one can
        // be fixed by asking for less, and the other cannot.
        InsufficientBalanceException thrown = (InsufficientBalanceException)
                org.assertj.core.api.Assertions.catchThrowable(
                        () -> savings("1500.00").debit(new BigDecimal("1000.00")));

        assertThat(thrown.getReason())
                .isEqualTo(InsufficientBalanceException.Reason.WOULD_BREACH_MINIMUM_BALANCE);
        assertThat(thrown.getAvailable()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("the balance is unchanged when a debit is refused")
    void refusedDebitLeavesBalanceAlone() {
        Account account = savings("1500.00");

        assertThatThrownBy(() -> account.debit(new BigDecimal("1000.00")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("a zero or negative amount is refused on both paths")
    void refusesNonPositiveAmounts() {
        // A negative debit is a credit by another name and would bypass every
        // balance check above it.
        for (String bad : new String[]{"0.00", "-100.00"}) {
            assertThatThrownBy(() -> savings("5000.00").debit(new BigDecimal(bad)))
                    .isInstanceOf(InvalidTransactionException.class);
            assertThatThrownBy(() -> savings("5000.00").credit(new BigDecimal(bad)))
                    .isInstanceOf(InvalidTransactionException.class);
        }
    }

    @Test
    @DisplayName("a frozen account refuses debits and credits")
    void frozenRefusesBoth() {
        Account frozen = account(AccountType.SAVINGS, "5000.00", "1000.00",
                AccountStatus.FROZEN);

        // Blocking only debits would let money keep arriving in an account under
        // investigation, which is the opposite of what a freeze is for.
        assertThatThrownBy(() -> frozen.debit(new BigDecimal("100.00")))
                .isInstanceOf(InvalidTransactionException.class);
        assertThatThrownBy(() -> frozen.credit(new BigDecimal("100.00")))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    @DisplayName("a closed account refuses credits, so money cannot be stranded")
    void closedRefusesCredit() {
        Account closed = account(AccountType.SAVINGS, "0.00", "1000.00",
                AccountStatus.CLOSED);

        assertThatThrownBy(() -> closed.credit(new BigDecimal("100.00")))
                .isInstanceOf(InvalidTransactionException.class)
                .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                .isEqualTo(InvalidTransactionException.Reason.DESTINATION_NOT_ACTIVE);
    }

    @Test
    @DisplayName("closure drains the balance past the minimum, or it could never close")
    void closureIgnoresMinimumBalance() {
        // A savings account with a 1000 floor sitting at exactly 1000 cannot be
        // withdrawn from at all. If the same rule applied to closing, the
        // customer's money would be trapped by a rule meant to protect it.
        Account account = savings("1000.00");
        assertThat(account.availableBalance()).isEqualByComparingTo("0.00");

        BigDecimal paidOut = account.drainForClosure();

        assertThat(paidOut).isEqualByComparingTo("1000.00");
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a salary account has no floor, so it can be emptied")
    void salaryAccountHasNoFloor() {
        Account salary = account(AccountType.SALARY, "45000.00", "0.00",
                AccountStatus.ACTIVE);

        salary.debit(new BigDecimal("45000.00"));
        assertThat(salary.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("credit and debit are exact in BigDecimal")
    void moneyArithmeticIsExact() {
        // The reason every monetary field is BigDecimal: 0.10 has no exact binary
        // representation, and a bank cannot reconcile a ledger that is a cent out.
        Account account = savings("1000.00");
        for (int i = 0; i < 10; i++) {
            account.credit(new BigDecimal("0.10"));
        }
        assertThat(account.getBalance()).isEqualByComparingTo("1001.00");
    }
}
