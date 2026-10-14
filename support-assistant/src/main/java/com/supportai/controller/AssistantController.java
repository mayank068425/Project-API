package com.supportai.controller;

import com.supportai.dto.request.AskRequest;
import com.supportai.dto.request.CustomerUpdateRequest;
import com.supportai.dto.response.AnswerResponse;
import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.DraftResponse;
import com.supportai.dto.response.RcaResponse;
import com.supportai.dto.response.SuggestionResponse;
import com.supportai.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The AI features.
 *
 * <p>Every endpoint here can decline. A 422 from this controller usually means
 * the system worked correctly: it was asked for something it could not ground and
 * refused rather than inventing one. Only 503 is worth retrying.
 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant", description = "Every output verified; unverifiable output withheld")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question of the runbooks",
            description = "Citations are checked against the retrieved chunks and quoted "
                    + "text against their content. An answer that fails is WITHHELD, not "
                    + "returned with a warning — use /api/knowledge/search to read the "
                    + "sources directly.")
    public ApiResponse<AnswerResponse> ask(@Valid @RequestBody AskRequest request) {
        AnswerResponse answer = assistantService.ask(request);
        return ApiResponse.ok(answer, answer.warnings().isEmpty() ? null
                : "This answer carries " + answer.warnings().size() + " warning(s).");
    }

    @PostMapping("/incidents/{id}/rca")
    @Operation(summary = "Draft a root cause analysis",
            description = "Refused below the evidence floor. Findings must cite timeline "
                    + "entries; uncited claims are downgraded to inferences and a draft "
                    + "supported by nothing is returned as INSUFFICIENT_EVIDENCE. Never "
                    + "writes to the incident's root cause field.")
    public ApiResponse<RcaResponse> rca(@PathVariable Long id) {
        RcaResponse rca = assistantService.analyseRootCause(id);
        return ApiResponse.ok(rca, rca.corrections().isEmpty() ? rca.note()
                : rca.note() + " " + rca.corrections().size()
                        + " correction(s) were applied before you saw this.");
    }

    @PostMapping("/incidents/{id}/precedents")
    @Operation(summary = "Past incidents whose resolution might apply",
            description = "Led by each precedent's ROOT CAUSE, not its fix. Similar "
                    + "symptoms routinely have different causes, and applying a fix without "
                    + "confirming the cause matches is how the wrong change gets made "
                    + "confidently. Ranked arithmetically; no model reorders this.")
    public ApiResponse<List<SuggestionResponse>> precedents(@PathVariable Long id) {
        List<SuggestionResponse> suggestions = assistantService.suggestPrecedents(id);
        return ApiResponse.ok(suggestions, suggestions.isEmpty() ? null
                : "Confirm the root cause matches before applying any of these resolutions.");
    }

    @PostMapping("/incidents/{id}/summarise")
    @Operation(summary = "Summarise a long description",
            description = "Stored beside the original, never replacing it, and read by "
                    + "nothing else in the system.")
    public ApiResponse<Map<String, String>> summarise(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("summary", assistantService.summarise(id)));
    }

    @PostMapping("/incidents/{id}/customer-update")
    @Operation(summary = "Draft a customer update",
            description = "Internal identifiers are redacted AFTER generation — the prompt "
                    + "is advisory, the regex is not. Commitment language is flagged rather "
                    + "than deleted, because a restoration-time promise belongs in front of "
                    + "whoever is accountable for it.")
    public ApiResponse<DraftResponse> customerUpdate(
            @PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        DraftResponse draft = assistantService.draftCustomerUpdate(id, request.audience());
        return ApiResponse.ok(draft, draft.commitmentsToReview().isEmpty()
                ? "Draft only. Review before sending."
                : "Draft contains " + draft.commitmentsToReview().size()
                        + " commitment(s) that need approval before sending.");
    }

    @PostMapping("/incidents/{id}/post-incident-report")
    @Operation(summary = "Draft an internal post-incident report",
            description = "No redaction: the detail is the point. Still a draft.")
    public ApiResponse<DraftResponse> postIncidentReport(@PathVariable Long id) {
        return ApiResponse.ok(assistantService.draftPostIncidentReport(id),
                "Draft only. A report nobody edited is a report nobody stands behind.");
    }
}
