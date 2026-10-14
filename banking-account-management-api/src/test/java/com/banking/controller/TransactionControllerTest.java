package com.banking.controller;

import com.banking.dto.request.TransferRequest;
import com.banking.dto.response.TransferResponse;
import com.banking.exception.GlobalExceptionHandler;
import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web layer in isolation.
 *
 * <p>Standalone MockMvc rather than {@code @WebMvcTest}, so no Spring context
 * starts and these run in milliseconds. The trade-off is honest: security filters
 * and {@code @PreAuthorize} are not exercised, so these prove the mappings, the
 * envelope and the status codes — not the authorisation rules. Those need the
 * full context.
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String transferBody(String from, String to, String amount) throws Exception {
        return objectMapper.writeValueAsString(
                new TransferRequest(from, to, new BigDecimal(amount), "Test", null));
    }

    @Test
    @DisplayName("a successful transfer returns 201 and both account numbers")
    void transferReturns201() throws Exception {
        when(transactionService.transfer(any())).thenReturn(new TransferResponse(
                "TXNABC123", "000001000017", "000001000033", "Arjun Rao",
                new BigDecimal("5000.00"), new BigDecimal("20000.00"),
                Instant.now(), "Test"));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "5000.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionReference").value("TXNABC123"))
                .andExpect(jsonPath("$.data.toAccountName").value("Arjun Rao"));
    }

    @Test
    @DisplayName("insufficient balance is 422 and says how much is short")
    void insufficientBalanceIs422WithDetail() throws Exception {
        // A bare "declined" leaves the customer unable to tell whether asking for
        // less would work, which turns into a support call.
        when(transactionService.transfer(any())).thenThrow(
                new InsufficientBalanceException("Balance is below the requested amount",
                        new BigDecimal("2000.00"), new BigDecimal("5000.00"),
                        InsufficientBalanceException.Reason.BALANCE_TOO_LOW));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "5000.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.data.available").value(2000.00))
                .andExpect(jsonPath("$.data.shortfall").value(3000.00))
                .andExpect(jsonPath("$.data.reason").value("BALANCE_TOO_LOW"));
    }

    @Test
    @DisplayName("transferring to somebody else's account is 403")
    void notOwnedIs403() throws Exception {
        when(transactionService.transfer(any())).thenThrow(
                new InvalidTransactionException("That account is not yours",
                        InvalidTransactionException.Reason.ACCOUNT_NOT_OWNED));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "100.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSACTION"));
    }

    @Test
    @DisplayName("an unverified payee is 422, not 403")
    void unverifiedBeneficiaryIs422() throws Exception {
        // A business refusal, not an authorisation one. The caller may perform
        // the operation; this particular destination is not yet allowed.
        when(transactionService.transfer(any())).thenThrow(
                new InvalidTransactionException("This payee has not been verified.",
                        InvalidTransactionException.Reason.BENEFICIARY_NOT_VERIFIED));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "100.00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("BENEFICIARY_NOT_VERIFIED")));
    }

    @Test
    @DisplayName("lock contention is 429, so the client retries rather than giving up")
    void lockContentionIs429() throws Exception {
        // A 500 tells a client to abandon something that would succeed a moment
        // later. The request was valid; another transaction simply held the row.
        when(transactionService.transfer(any()))
                .thenThrow(new PessimisticLockingFailureException("lock wait timeout"));

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "100.00")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("a zero amount fails validation before reaching the service")
    void zeroAmountIsRejected() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amount"));
    }

    @Test
    @DisplayName("a negative amount is rejected too")
    void negativeAmountIsRejected() throws Exception {
        // Otherwise a negative transfer reverses the direction of the money.
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("000001000017", "000001000033", "-500.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("an unknown transaction is 404")
    void unknownTransactionIs404() throws Exception {
        when(transactionService.get(any()))
                .thenThrow(ResourceNotFoundException.of("transaction", 99));

        mockMvc.perform(get("/api/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
