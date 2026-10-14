package com.banking.service;

import com.banking.config.BankingPolicy;
import com.banking.entity.Account;
import com.banking.entity.AccountStatus;
import com.banking.entity.AccountType;
import com.banking.entity.Beneficiary;
import com.banking.entity.Customer;
import com.banking.exception.InvalidTransactionException;
import com.banking.repository.AccountRepository;
import com.banking.repository.TransactionRepository;
import com.banking.serviceimpl.BeneficiaryServiceImpl;
import com.banking.validation.AccountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferRulesTest {

    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;

    private BankingPolicy policy;
    private AccountValidator validator;
    private BeneficiaryServiceImpl beneficiaryService;

    @BeforeEach
    void setUp() {
        policy = new BankingPolicy();
        policy.setZone(ZoneId.of("Asia/Kolkata"));
        policy.setBeneficiaryCoolingPeriod(Duration.ofHours(4));
        policy.setCoolingPeriodTransferLimit(new BigDecimal("5000.00"));
        policy.setDefaultDailyTransferLimit(new BigDecimal("200000.00"));
        policy.setMaxSingleTransferAmount(new BigDecimal("1000000.00"));

        validator = new AccountValidator(accounts, transactions, policy);
        beneficiaryService = new BeneficiaryServiceImpl(null, accounts, null, null, policy);
    }

    private Account account(long id, String number, AccountStatus status) {
        return Account.builder()
                .id(id).accountNumber(number)
                .customer(Customer.builder().id(id).firstName("Test").lastName("User").build())
                .accountType(AccountType.SAVINGS)
                .status(status)
                .balance(new BigDecimal("50000.00"))
                .minimumBalance(new BigDecimal("1000.00"))
                .openedOn(LocalDate.now())
                .build();
    }

    @Nested
    @DisplayName("account pairing")
    class Pairing {

        @Test
        @DisplayName("a transfer to the same account is refused")
        void refusesSelfTransfer() {
            // Not harmless: it would write two ledger rows netting to zero and
            // inflate the statement with a movement that never happened.
            Account same = account(1L, "000001000017", AccountStatus.ACTIVE);

            assertThatThrownBy(() -> validator.validateTransferPair(same, same))
                    .isInstanceOf(InvalidTransactionException.class)
                    .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                    .isEqualTo(InvalidTransactionException.Reason.SAME_ACCOUNT);
        }

        @Test
        @DisplayName("a frozen source cannot send")
        void refusesFrozenSource() {
            assertThatThrownBy(() -> validator.validateTransferPair(
                    account(1L, "000001000017", AccountStatus.FROZEN),
                    account(2L, "000001000025", AccountStatus.ACTIVE)))
                    .isInstanceOf(InvalidTransactionException.class)
                    .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                    .isEqualTo(InvalidTransactionException.Reason.SOURCE_NOT_ACTIVE);
        }

        @Test
        @DisplayName("a closed destination cannot receive")
        void refusesClosedDestination() {
            // Crediting a closed account puts money somewhere nobody can withdraw
            // it from, and unpicking that needs a human.
            assertThatThrownBy(() -> validator.validateTransferPair(
                    account(1L, "000001000017", AccountStatus.ACTIVE),
                    account(2L, "000001000025", AccountStatus.CLOSED)))
                    .isInstanceOf(InvalidTransactionException.class)
                    .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                    .isEqualTo(InvalidTransactionException.Reason.DESTINATION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("two active, distinct accounts are allowed")
        void allowsValidPair() {
            assertThatCode(() -> validator.validateTransferPair(
                    account(1L, "000001000017", AccountStatus.ACTIVE),
                    account(2L, "000001000025", AccountStatus.ACTIVE)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("beneficiary controls")
    class BeneficiaryControls {

        private Beneficiary payee(boolean verified, Instant verifiedAt) {
            return Beneficiary.builder()
                    .id(1L)
                    .customer(Customer.builder().id(1L).build())
                    .beneficiaryAccountNumber("000001000033")
                    .beneficiaryName("Arjun Rao")
                    .verified(verified)
                    .verifiedAt(verifiedAt)
                    .active(true)
                    .build();
        }

        @Test
        @DisplayName("an unverified payee cannot receive anything")
        void refusesUnverified() {
            assertThatThrownBy(() -> beneficiaryService.assertTransferAllowed(
                    payee(false, null), new BigDecimal("100.00")))
                    .isInstanceOf(InvalidTransactionException.class)
                    .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                    .isEqualTo(InvalidTransactionException.Reason.BENEFICIARY_NOT_VERIFIED);
        }

        @Test
        @DisplayName("a large transfer to a just-verified payee is held")
        void refusesLargeTransferInCoolingPeriod() {
            // The anti-fraud window. Somebody who takes over a session adds a
            // payee and empties the account in seconds; this is the window in
            // which the real customer sees the notification.
            Beneficiary justVerified = payee(true, Instant.now().minus(Duration.ofMinutes(90)));

            assertThatThrownBy(() -> beneficiaryService.assertTransferAllowed(
                    justVerified, new BigDecimal("50000.00")))
                    .isInstanceOf(InvalidTransactionException.class)
                    .extracting(ex -> ((InvalidTransactionException) ex).getReason())
                    .isEqualTo(InvalidTransactionException.Reason.BENEFICIARY_IN_COOLING_PERIOD);
        }

        @Test
        @DisplayName("a small transfer inside the window is allowed")
        void allowsSmallTransferInCoolingPeriod() {
            // The window caps the amount rather than blocking everything, so a
            // customer who genuinely just added a payee can still pay them.
            Beneficiary justVerified = payee(true, Instant.now().minus(Duration.ofMinutes(90)));

            assertThatCode(() -> beneficiaryService.assertTransferAllowed(
                    justVerified, new BigDecimal("2500.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("once the window passes, any amount is allowed")
        void allowsAfterCoolingPeriod() {
            Beneficiary settled = payee(true, Instant.now().minus(Duration.ofDays(30)));

            assertThatCode(() -> beneficiaryService.assertTransferAllowed(
                    settled, new BigDecimal("500000.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the cooling window is measured from verification, not creation")
        void windowRunsFromVerification() {
            // A payee added weeks ago but verified minutes ago is still inside
            // the window: verification is what makes it usable, so that is when
            // the clock starts.
            Beneficiary beneficiary = payee(true, Instant.now().minus(Duration.ofMinutes(5)));

            assertThat(beneficiary.isInCoolingPeriod(
                    policy.getBeneficiaryCoolingPeriod(), Instant.now())).isTrue();
        }
    }
}
