package com.supportai.dto.request;

import com.supportai.util.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Account creation.
 *
 * <p>No role field. Roles carry the retrieval clearance, so an endpoint accepting
 * one from the request body lets anybody grant themselves access to every
 * RESTRICTED runbook in the knowledge base.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 4, max = 80) String username,
        @NotBlank @Email(message = "Email must be valid") @Size(max = 150) String email,
        @NotBlank @Size(max = 150) String displayName,
        @NotBlank @Size(min = Constants.PASSWORD_MIN_LENGTH, max = 100,
                message = "Password must be at least 12 characters") String password,
        @Size(max = 80) String team) {
}
