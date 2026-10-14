package com.banking.dto.request;

import com.banking.util.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Self-registration, creating a login and a customer record together.
 *
 * <p>No role field. An endpoint that accepts a role from the request body grants
 * bank-employee rights to anyone who reads the API documentation; registration
 * always produces CUSTOMER and staff accounts are created by an administrator.
 *
 * <p>Date of birth rather than age, because age gates account opening and a
 * stored age is wrong from the customer's next birthday onwards.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 80) String username,
        @NotBlank @Email(message = "Email must be valid") @Size(max = 150) String email,
        @NotBlank @Size(min = Constants.PASSWORD_MIN_LENGTH, max = 100,
                message = "Password must be at least 12 characters") String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone must be 10 to 15 digits") String phone,
        @NotNull @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        @Size(max = 300) String address,
        @Size(max = 40) String nationalId) {
}
