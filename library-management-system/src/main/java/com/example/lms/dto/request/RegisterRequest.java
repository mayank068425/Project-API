package com.example.lms.dto.request;

import com.example.lms.util.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-registration.
 *
 * <p>Note the absence of a role field. An endpoint that takes a role from the
 * request body grants librarian rights to anyone who reads the API
 * documentation; self-registration always produces MEMBER and staff accounts are
 * created by an administrator.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 80) String username,
        @NotBlank @Email(message = "Email must be valid") @Size(max = 150) String email,
        @NotBlank @Size(min = Constants.PASSWORD_MIN_LENGTH, max = 100,
                message = "Password must be at least 12 characters") String password,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone must be 10 to 15 digits") String phone,
        @Size(max = 300) String address) {
}
