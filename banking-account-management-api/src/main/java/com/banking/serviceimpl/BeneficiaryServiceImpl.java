package com.banking.serviceimpl;

import com.banking.config.BankingPolicy;
import com.banking.dto.request.BeneficiaryRequest;
import com.banking.dto.response.BeneficiaryResponse;
import com.banking.entity.Account;
import com.banking.entity.Beneficiary;
import com.banking.entity.Customer;
import com.banking.exception.BusinessRuleException;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.BeneficiaryMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.BeneficiaryRepository;
import com.banking.security.SecurityUtils;
import com.banking.service.BeneficiaryService;
import com.banking.service.CustomerService;
import com.banking.util.AccountNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Saved payees.
 *
 * <p>Two controls, doing different jobs. <b>Verification</b> confirms the
 * destination exists — a transposed digit otherwise succeeds silently into a
 * stranger's account. The <b>cooling-off window</b> is anti-fraud: somebody who
 * takes over a session adds a payee and empties the account in seconds, and a few
 * hours at a low cap is the window in which the real customer sees the
 * notification.
 *
 * <p>Both are enforced from the transfer path via
 * {@link #assertTransferAllowed}, not only when the record is created. A rule
 * checked once at add time is not a rule.
 */
@Service
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryServiceImpl.class);

    private final BeneficiaryRepository beneficiaries;
    private final AccountRepository accounts;
    private final CustomerService customerService;
    private final BeneficiaryMapper mapper;
    private final BankingPolicy policy;

    public BeneficiaryServiceImpl(BeneficiaryRepository beneficiaries,
                                  AccountRepository accounts,
                                  CustomerService customerService,
                                  BeneficiaryMapper mapper, BankingPolicy policy) {
        this.beneficiaries = beneficiaries;
        this.accounts = accounts;
        this.customerService = customerService;
        this.mapper = mapper;
        this.policy = policy;
    }

    @Override
    @Transactional
    public BeneficiaryResponse add(BeneficiaryRequest request) {
        Long customerId = requireCustomerId();
        Customer customer = customerService.requireCustomer(customerId);

        String accountNumber = request.beneficiaryAccountNumber().strip();

        // The check digit is a typo filter, not security. It turns "your money
        // went to a stranger" into a validation message before anything is saved.
        if (!AccountNumberGenerator.isValid(accountNumber)) {
            throw new BusinessRuleException(
                    "That account number's check digit is wrong. Check the digits.");
        }

        // Own accounts do not need to be beneficiaries; transfers between them
        // are already permitted, and listing yourself as a payee is confusing.
        accounts.findByAccountNumber(accountNumber).ifPresent(account -> {
            if (account.getCustomer().getId().equals(customerId)) {
                throw new BusinessRuleException(
                        "That is your own account; it does not need to be added as a payee");
            }
        });

        if (beneficiaries.existsByCustomerIdAndBeneficiaryAccountNumberAndActiveTrue(
                customerId, accountNumber)) {
            throw new DuplicateResourceException("That payee is already saved");
        }
        if (beneficiaries.countByCustomerIdAndActiveTrue(customerId)
                >= policy.getMaxBeneficiariesPerCustomer()) {
            throw new BusinessRuleException("You have reached the limit of "
                    + policy.getMaxBeneficiariesPerCustomer() + " saved payees");
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .customer(customer)
                .beneficiaryAccountNumber(accountNumber)
                .beneficiaryName(request.beneficiaryName().strip())
                .nickname(request.nickname())
                .bankName(request.bankName())
                .ifscCode(request.ifscCode())
                // Unverified on creation. Nothing can be sent here yet.
                .verified(false)
                .active(true)
                .build();
        beneficiaries.save(beneficiary);

        log.info("customer {} added payee ending {}", customerId,
                accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
        return mapper.toResponse(beneficiary, policy.getBeneficiaryCoolingPeriod());
    }

    @Override
    @Transactional
    public BeneficiaryResponse update(Long id, BeneficiaryRequest request) {
        Beneficiary beneficiary = requireOwn(id);

        // Only the label is editable. Changing the account number would silently
        // repoint a verified payee at somewhere nobody confirmed — the exact
        // attack verification exists to stop. A new destination is a new payee.
        if (request.nickname() != null) {
            beneficiary.setNickname(request.nickname());
        }
        if (request.bankName() != null) {
            beneficiary.setBankName(request.bankName());
        }

        beneficiaries.save(beneficiary);
        return mapper.toResponse(beneficiary, policy.getBeneficiaryCoolingPeriod());
    }

    /**
     * Confirms the payee against the bank's own records.
     *
     * <p>The confirmed name comes from the account, not from what the payer
     * typed. A payer who mistypes both the number and the name would otherwise
     * see a saved payee that looks entirely correct, and the mismatch — the one
     * signal that something is wrong — would be hidden.
     */
    @Override
    @Transactional
    public BeneficiaryResponse verify(Long id) {
        Beneficiary beneficiary = requireOwn(id);

        if (beneficiary.isVerified()) {
            throw new BusinessRuleException("That payee is already verified");
        }

        Account destination = accounts
                .findByAccountNumberWithCustomer(beneficiary.getBeneficiaryAccountNumber())
                .orElseThrow(() -> new BusinessRuleException(
                        "No account with that number could be found. "
                                + "Check the number with the payee."));

        if (!destination.getStatus().canCredit()) {
            throw new BusinessRuleException("That account cannot currently receive payments");
        }

        beneficiary.markVerified(SecurityUtils.currentUsername(),
                destination.getCustomer().getFullName());
        beneficiaries.save(beneficiary);

        log.info("payee {} verified for customer {}", id, beneficiary.getCustomer().getId());
        return mapper.toResponse(beneficiary, policy.getBeneficiaryCoolingPeriod());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Beneficiary beneficiary = requireOwn(id);
        // Soft, so a past transfer can still be explained by reference to the
        // payee it went to.
        beneficiary.setActive(false);
        beneficiaries.save(beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BeneficiaryResponse> list(Pageable pageable) {
        return beneficiaries.findByCustomer(requireCustomerId(), pageable)
                .map(b -> mapper.toResponse(b, policy.getBeneficiaryCoolingPeriod()));
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse get(Long id) {
        return mapper.toResponse(requireOwn(id), policy.getBeneficiaryCoolingPeriod());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Beneficiary> findForTransfer(Long customerId, String toAccountNumber) {
        return beneficiaries.findByCustomerIdAndBeneficiaryAccountNumber(
                customerId, toAccountNumber);
    }

    /**
     * Whether this payee may receive this amount right now.
     *
     * <p>Called from the transfer path. Both refusals name what is wrong and what
     * would fix it, because "transfer declined" with no reason produces a support
     * call rather than a corrected request.
     */
    @Override
    public void assertTransferAllowed(Beneficiary beneficiary, BigDecimal amount) {
        if (!beneficiary.isActive() || !beneficiary.isVerified()) {
            throw new InvalidTransactionException(
                    "This payee has not been verified. Verify it before sending money.",
                    InvalidTransactionException.Reason.BENEFICIARY_NOT_VERIFIED);
        }

        if (beneficiary.isInCoolingPeriod(policy.getBeneficiaryCoolingPeriod(), Instant.now())
                && amount.compareTo(policy.getCoolingPeriodTransferLimit()) > 0) {
            Instant clearsAt = beneficiary.getVerifiedAt()
                    .plus(policy.getBeneficiaryCoolingPeriod());
            throw new InvalidTransactionException(
                    "This payee was verified recently. Transfers above "
                            + policy.getCoolingPeriodTransferLimit()
                            + " are held until " + clearsAt + ".",
                    InvalidTransactionException.Reason.BENEFICIARY_IN_COOLING_PERIOD);
        }
    }

    private Beneficiary requireOwn(Long id) {
        Beneficiary beneficiary = beneficiaries.findByIdWithCustomer(id)
                .orElseThrow(() -> ResourceNotFoundException.of("beneficiary", id));

        // Not-found rather than forbidden. A 403/404 split would let somebody
        // enumerate which beneficiary ids exist across the whole bank.
        if (!SecurityUtils.isStaff()
                && !beneficiary.getCustomer().getId().equals(SecurityUtils.currentCustomerId())) {
            throw ResourceNotFoundException.of("beneficiary", id);
        }
        return beneficiary;
    }

    private Long requireCustomerId() {
        Long customerId = SecurityUtils.currentCustomerId();
        if (customerId == null) {
            throw new BusinessRuleException(
                    "This login is not attached to a customer and cannot hold payees");
        }
        return customerId;
    }
}
