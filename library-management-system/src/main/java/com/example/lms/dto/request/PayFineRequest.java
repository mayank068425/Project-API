package com.example.lms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayFineRequest(
        @NotNull Long fineId,
        @NotNull @DecimalMin(value = "0.01", message = "Payment must be greater than zero")
        @Digits(integer = 8, fraction = 2) BigDecimal amount) {
}
