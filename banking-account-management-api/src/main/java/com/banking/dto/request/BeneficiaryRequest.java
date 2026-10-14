package com.banking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param beneficiaryName what the payer believes the payee is called. Replaced
 *                        with the bank's own record at verification, because a
 *                        payer who mistypes both number and name would otherwise
 *                        see a payee that looks entirely correct.
 */
public record BeneficiaryRequest(
        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
        String beneficiaryAccountNumber,
        @NotBlank @Size(max = 150) String beneficiaryName,
        @Size(max = 80) String nickname,
        @Size(max = 150) String bankName,
        @Size(max = 20) String ifscCode) {
}
