package com.banking.serviceimpl;

import com.banking.dto.request.CustomerRequest;
import com.banking.dto.response.AccountResponse;
import com.banking.dto.response.CustomerResponse;
import com.banking.entity.Customer;
import com.banking.entity.KycStatus;
import com.banking.exception.BusinessRuleException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.mapper.AccountMapper;
import com.banking.mapper.CustomerMapper;
import com.banking.repository.AccountRepository;
import com.banking.repository.CustomerRepository;
import com.banking.security.SecurityUtils;
import com.banking.service.CustomerService;
import com.banking.util.AccountNumberGenerator;
import com.banking.validation.CustomerValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CustomerMapper customerMapper;
    private final AccountMapper accountMapper;
    private final CustomerValidator validator;
    private final AccountNumberGenerator numbers;

    public CustomerServiceImpl(CustomerRepository customers, AccountRepository accounts,
                               CustomerMapper customerMapper, AccountMapper accountMapper,
                               CustomerValidator validator, AccountNumberGenerator numbers) {
        this.customers = customers;
        this.accounts = accounts;
        this.customerMapper = customerMapper;
        this.accountMapper = accountMapper;
        this.validator = validator;
        this.numbers = numbers;
    }

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        validator.validateForCreate(request);

        Customer customer = Customer.builder()
                .customerNumber(numbers.nextCustomerNumber())
                .firstName(request.firstName().strip())
                .lastName(request.lastName().strip())
                .email(request.email().toLowerCase().strip())
                .phone(request.phone().strip())
                .dateOfBirth(request.dateOfBirth())
                .address(request.address())
                .city(request.city())
                .postalCode(request.postalCode())
                .nationalId(blankToNull(request.nationalId()))
                // PENDING, not VERIFIED. A record can exist while documents are
                // checked; no account opens against it until they pass.
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .build();
        customers.save(customer);

        log.info("created customer {}", customer.getCustomerNumber());
        return customerMapper.toResponse(customer, 0);
    }

    @Override
    @Transactional
    public Customer createForSelfRegistration(String firstName, String lastName, String email,
                                              String phone, LocalDate dateOfBirth,
                                              String address, String nationalId) {
        String normalisedEmail = email.toLowerCase().strip();
        if (customers.existsByEmail(normalisedEmail)) {
            throw new BusinessRuleException("A customer with that email already exists");
        }

        Customer customer = Customer.builder()
                .customerNumber(numbers.nextCustomerNumber())
                .firstName(firstName.strip())
                .lastName(lastName.strip())
                .email(normalisedEmail)
                .phone(phone.strip())
                .dateOfBirth(dateOfBirth)
                .address(address)
                .nationalId(blankToNull(nationalId))
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .build();
        return customers.save(customer);
    }

    @Override
    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        SecurityUtils.requireSelfOrStaff(id);
        Customer customer = requireCustomer(id);
        validator.validateForUpdate(request, customer);

        if (request.firstName() != null) {
            customer.setFirstName(request.firstName().strip());
        }
        if (request.lastName() != null) {
            customer.setLastName(request.lastName().strip());
        }
        if (request.email() != null) {
            customer.setEmail(request.email().toLowerCase().strip());
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone().strip());
        }
        if (request.address() != null) {
            customer.setAddress(request.address());
        }
        if (request.city() != null) {
            customer.setCity(request.city());
        }
        if (request.postalCode() != null) {
            customer.setPostalCode(request.postalCode());
        }
        if (request.dateOfBirth() != null) {
            // Staff only. A customer editing their own date of birth could clear
            // an age restriction on their account.
            SecurityUtils.requireStaff("Changing a date of birth");
            customer.setDateOfBirth(request.dateOfBirth());
        }
        // nationalId is not editable here at all. Correcting it is an identity
        // operation that needs documents, not a PUT.

        customers.save(customer);
        return customerMapper.toResponse(customer, openAccountCount(id));
    }

    /**
     * Soft-deletes a customer, refusing while any account is open.
     *
     * <p>The relationship is mapped without a remove cascade for exactly this
     * reason: cascading would delete the customer's accounts and, through them,
     * the ledger rows those accounts appear in. One call and a person's entire
     * banking history is gone.
     */
    @Override
    @Transactional
    public void delete(Long id) {
        SecurityUtils.requireStaff("Deleting a customer");
        Customer customer = requireCustomer(id);

        long open = customers.countOpenAccounts(id);
        if (open > 0) {
            throw new BusinessRuleException("Customer " + customer.getCustomerNumber()
                    + " still has " + open + " open account(s). Close them first.");
        }

        customer.setActive(false);
        customers.save(customer);
        log.info("deactivated customer {}", customer.getCustomerNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        SecurityUtils.requireSelfOrStaff(id);
        return customerMapper.toResponse(requireCustomer(id), openAccountCount(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, Pageable pageable) {
        SecurityUtils.requireStaff("Listing customers");
        Page<Customer> page = (query != null && !query.isBlank())
                ? customers.search(query.strip(), pageable)
                : customers.findByActiveTrue(pageable);
        return page.map(customerMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> accountsOf(Long customerId) {
        SecurityUtils.requireSelfOrStaff(customerId);
        requireCustomer(customerId);
        return accounts.findByCustomer(customerId, Pageable.unpaged())
                .map(accountMapper::toResponse).getContent();
    }

    @Override
    @Transactional
    public CustomerResponse setKycStatus(Long id, KycStatus status, String reason) {
        // Only the bank decides this. A customer marking their own KYC verified
        // would be the whole control defeated in one request.
        SecurityUtils.requireStaff("Changing KYC status");
        Customer customer = requireCustomer(id);
        KycStatus before = customer.getKycStatus();

        customer.setKycStatus(status);
        customers.save(customer);

        log.info("customer {} KYC {} -> {}{}", customer.getCustomerNumber(), before, status,
                reason == null ? "" : " (" + reason + ")");
        return customerMapper.toResponse(customer, openAccountCount(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Customer requireCustomer(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("customer", id));
    }

    private int openAccountCount(Long customerId) {
        return (int) customers.countOpenAccounts(customerId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
