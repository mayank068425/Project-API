package com.banking.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create or update a customer.
 *
 * <p>One record for both, with every field nullable. On update a null field
 * means "leave it alone", which is what lets a teller change a phone number
 * without resending the whole record. {@code CustomerValidator} enforces what is
 * mandatory on create, because a bean-validation annotation cannot say "required
 * here, optional there".
 */
public record CustomerRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Email(message = "Email must be valid") @Size(max = 150) String email,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone must be 10 to 15 digits") String phone,
        @Past LocalDate dateOfBirth,
        @Size(max = 300) String address,
        @Size(max = 80) String city,
        @Size(max = 20) String postalCode,
        @Size(max = 40) String nationalId) {
}
