package com.supportai.dto.request;

import com.supportai.util.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = Constants.PASSWORD_MIN_LENGTH,
                message = "Password must be at least 12 characters") String newPassword) {
}
