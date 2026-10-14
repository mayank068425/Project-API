package com.banking.validation;

import com.banking.config.BankingPolicy;
import com.banking.dto.request.CustomerRequest;
import com.banking.entity.Customer;
import com.banking.exception.BusinessRuleException;
import com.banking.repository.CustomerRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Customer rules a Bean Validation annotation cannot express.
 *
 * <p>Three kinds: fields mandatory on create but optional on update, which no
 * single annotation can state; rules depending on the current state of the row;
 * and checks needing a database lookup.
 */
@Component
public class CustomerValidator {

    /** Nobody has verifiably lived past this. Beyond it, the date is a typo. */
    private static final int MAX_PLAUSIBLE_AGE = 130;

    private final CustomerRepository customers;
    private final BankingPolicy policy;

    public CustomerValidator(CustomerRepository customers, BankingPolicy policy) {
        this.customers = customers;
        this.policy = policy;
    }

    public void validateForCreate(CustomerRequest request) {
        requirePresent(request.firstName(), "First name");
        requirePresent(request.lastName(), "Last name");
        requirePresent(request.email(), "Email");
        requirePresent(request.phone(), "Phone");

        if (request.dateOfBirth() == null) {
            throw new BusinessRuleException("Date of birth is required");
        }
        validateDateOfBirth(request.dateOfBirth());

        if (customers.existsByEmail(request.email().toLowerCase().strip())) {
            throw new BusinessRuleException("A customer with that email already exists");
        }
        if (request.nationalId() != null && !request.nationalId().isBlank()
                && customers.existsByNationalId(request.nationalId().strip())) {
            // Two customer records sharing a national identifier is either a
            // duplicate to be merged or an attempt to open a second identity.
            throw new BusinessRuleException(
                    "A customer with that national identifier already exists");
        }
    }

    public void validateForUpdate(CustomerRequest request, Customer existing) {
        if (request.dateOfBirth() != null) {
            validateDateOfBirth(request.dateOfBirth());
        }
        if (request.email() != null) {
            String email = request.email().toLowerCase().strip();
            customers.findByEmail(email).ifPresent(other -> {
                // Only a clash with a different customer is a duplicate, or
                // saving a record without changing its email would fail.
                if (!other.getId().equals(existing.getId())) {
                    throw new BusinessRuleException(
                            "Another customer already uses that email");
                }
            });
        }
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(policy.getZone());
        if (dateOfBirth.isAfter(today)) {
            throw new BusinessRuleException("Date of birth cannot be in the future");
        }
        if (dateOfBirth.isBefore(today.minusYears(MAX_PLAUSIBLE_AGE))) {
            throw new BusinessRuleException("Date of birth implies an age above "
                    + MAX_PLAUSIBLE_AGE + " years; check the value");
        }
    }

    private void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
    }
}
