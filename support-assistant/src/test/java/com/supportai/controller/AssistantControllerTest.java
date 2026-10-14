package com.supportai.controller;

import com.supportai.dto.request.AskRequest;
import com.supportai.exception.AssistantException;
import com.supportai.exception.GlobalExceptionHandler;
import com.supportai.service.AssistantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The status codes that carry the withhold-versus-fail distinction.
 *
 * <p>Standalone MockMvc, so no Spring context starts. Security filters are not
 * exercised — these prove the mappings and the status mapping, not the
 * authorisation rules.
 */
@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    @Mock
    private AssistantService assistantService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AssistantController(assistantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String ask() throws Exception {
        return objectMapper.writeValueAsString(
                new AskRequest("How do I restart the payments pods?", "payments"));
    }

    @Test
    @DisplayName("a rejected answer is 422 and marked not retryable")
    void rejectedAnswerIs422() throws Exception {
        // The system worked correctly: an answer was generated, failed citation
        // verification, and was withheld. Retrying will produce the same outcome,
        // so the client must not treat this as a transient failure.
        when(assistantService.ask(any())).thenThrow(new AssistantException(
                AssistantException.Kind.OUTPUT_REJECTED,
                "The generated answer referenced material that is not in the retrieved sources"));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON).content(ask()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ASSISTANT_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.kind").value("OUTPUT_REJECTED"))
                .andExpect(jsonPath("$.data.retryable").value(false));
    }

    @Test
    @DisplayName("insufficient context is 422, not an error")
    void insufficientContextIs422() throws Exception {
        when(assistantService.ask(any())).thenThrow(new AssistantException(
                AssistantException.Kind.INSUFFICIENT_CONTEXT,
                "Nothing in the knowledge base matches that question."));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON).content(ask()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.kind").value("INSUFFICIENT_CONTEXT"))
                .andExpect(jsonPath("$.data.retryable").value(false));
    }

    @Test
    @DisplayName("a provider outage is 503 and IS retryable")
    void providerOutageIs503() throws Exception {
        // The only one of the three worth retrying. Collapsing these into one code
        // would have clients retrying a refusal forever.
        when(assistantService.ask(any())).thenThrow(new AssistantException(
                AssistantException.Kind.PROVIDER_UNAVAILABLE, "The assistant is disabled."));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON).content(ask()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.kind").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.retryable").value(true));
    }

    @Test
    @DisplayName("an RCA below the evidence floor is 422")
    void insufficientEvidenceIs422() throws Exception {
        when(assistantService.analyseRootCause(anyLong())).thenThrow(new AssistantException(
                AssistantException.Kind.INSUFFICIENT_CONTEXT,
                "Incident INC-000001 has 1 timeline entries that count as evidence."));

        mockMvc.perform(post("/api/assistant/incidents/1/rca"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("count as evidence")));
    }

    @Test
    @DisplayName("a blank question fails validation before the service is called")
    void blankQuestionIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(new AskRequest("", null));

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
