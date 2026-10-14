package com.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Move money between two accounts.
 *
 * <p>Both sides are account <em>numbers</em> rather than internal ids. A customer
 * transferring money has the number from a cheque or a saved payee; exposing the
 * primary key would also let somebody probe the id space to discover which
 * accounts exist.
 *
 * @param idempotencyKey optional. A retried transfer — a flaky mobile
 *                       connection, a double-tapped button — must not move the
 *                       money twice. Supplying a key makes the operation safe to
 *                       repeat; without one, the caller owns that risk.
 */
public record TransferRequest(
        @NotBlank(message = "Source account is required") String fromAccountNumber,
        @NotBlank(message = "Destination account is required") String toAccountNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 300) String description,
        @Size(max = 64) String idempotencyKey) {
}
