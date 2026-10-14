package com.supportai.service;

import com.supportai.dto.request.AskRequest;
import com.supportai.dto.response.AnswerResponse;
import com.supportai.dto.response.DraftResponse;
import com.supportai.dto.response.RcaResponse;
import com.supportai.dto.response.SuggestionResponse;

import java.util.List;

/**
 * Everything that calls a language model.
 *
 * <p>Every method here can refuse. {@code AssistantException} with kind
 * {@code INSUFFICIENT_CONTEXT} or {@code OUTPUT_REJECTED} is the system working
 * correctly — it was asked for something it could not ground, and it declined
 * rather than inventing one. Only {@code PROVIDER_UNAVAILABLE} is a failure.
 */
public interface AssistantService {

    /**
     * Answers a question from the knowledge base, with verified citations.
     *
     * <p>An answer that cites a chunk it was not shown, or quotes text no source
     * contains, is withheld entirely. A caveat above an answer containing a subtly
     * wrong command does not stop anyone running it.
     */
    AnswerResponse ask(AskRequest request);

    /**
     * Drafts a root cause analysis from an incident's timeline.
     *
     * <p>Refused below the configured evidence floor. Never writes to the
     * incident's root cause field; a person does that after reading the draft.
     */
    RcaResponse analyseRootCause(Long incidentId);

    /**
     * Past incidents whose resolution might apply.
     *
     * <p>Ranked arithmetically, led by each precedent's root cause, with the
     * reasons and the cautions shown. No model reorders this list.
     */
    List<SuggestionResponse> suggestPrecedents(Long incidentId);

    /** Summarises a long description. Stored beside the original, never over it. */
    String summarise(Long incidentId);

    /** A customer update, redacted after generation and requiring approval. */
    DraftResponse draftCustomerUpdate(Long incidentId, String audience);

    /** An internal post-incident report. No redaction: the detail is the point. */
    DraftResponse draftPostIncidentReport(Long incidentId);
}
