package com.banking.mapper;

import com.banking.dto.response.CustomerResponse;
import com.banking.entity.Customer;
import org.springframework.stereotype.Component;

/**
 * Customer entity to response.
 *
 * <h2>Hand-written, despite ModelMapper being on the classpath</h2>
 * A reflective mapper copies every field whose name matches. That is convenient
 * and, for this entity, wrong: {@code nationalId} would be copied straight into
 * the response, and nothing would ever flag it. The omission here is the point of
 * the class, and it needs to be visible rather than implied by the absence of a
 * field on the target.
 *
 * <p>ModelMapper is configured strictly and used for the shapes where every
 * field genuinely is a straight copy.
 */
@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer, int accountCount) {
        if (customer == null) {
            return null;
        }
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getDateOfBirth(),
                // Derived on every read. A stored age would be wrong from the
                // customer's next birthday onwards, and it gates account opening.
                customer.getAge(),
                customer.getAddress(),
                customer.getCity(),
                customer.getPostalCode(),
                customer.getKycStatus(),
                customer.isActive(),
                accountCount);
        // nationalId is deliberately not mapped. See the class comment.
    }

    public CustomerResponse toResponse(Customer customer) {
        return toResponse(customer, 0);
    }
}
