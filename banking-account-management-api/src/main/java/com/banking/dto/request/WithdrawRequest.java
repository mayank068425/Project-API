package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WithdrawRequest(
        @NotBlank(message = "Account number is required") String accountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 300) String description) {
}
