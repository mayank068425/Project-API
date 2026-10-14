package com.supportai.serviceimpl;

import com.supportai.config.AssistantProperties;
import com.supportai.dto.request.AskRequest;
import com.supportai.dto.response.AnswerResponse;
import com.supportai.dto.response.DraftResponse;
import com.supportai.dto.response.RcaDraft;
import com.supportai.dto.response.RcaResponse;
import com.supportai.dto.response.SuggestionResponse;
import com.supportai.entity.Incident;
import com.supportai.entity.TimelineEntry;
import com.supportai.exception.AssistantException;
import com.supportai.mapper.DocumentMapper;
import com.supportai.repository.IncidentRepository;
import com.supportai.retrieval.HybridRetriever;
import com.supportai.retrieval.RetrievedChunk;
import com.supportai.security.SecurityUtils;
import com.supportai.service.AssistantService;
import com.supportai.service.AuditService;
import com.supportai.service.IncidentService;
import com.supportai.util.UntrustedText;
import com.supportai.validation.CitationVerifier;
import com.supportai.validation.EvidenceIndex;
import com.supportai.validation.RcaVerifier;
import com.supportai.validation.Redactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Everything that calls a language model.
 *
 * <h2>The rule this class implements</h2>
 * Every output is verified against what the model was actually shown, and
 * anything unverifiable is <b>withheld, not caveated</b>.
 *
 * <p>The alternative — showing the answer with a warning above it — does not
 * work during an incident. Nobody has time to check, the output is fluent and
 * correctly formatted, and a warning does not stop a subtly wrong command from
 * being pasted into a terminal. If the system cannot stand behind an answer, it
 * says so and points at the sources instead.
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);
    private static final int MIN_LENGTH_TO_SUMMARISE = 1200;

    private static final double SAME_SERVICE_MULTIPLIER = 1.35;
    private static final double SAME_SEVERITY_MULTIPLIER = 1.1;

    private final ChatClient chatClient;
    private final HybridRetriever retriever;
    private final PromptLibrary prompts;
    private final IncidentService incidentService;
    private final IncidentRepository incidents;
    private final DocumentMapper documentMapper;
    private final AssistantProperties properties;
    private final AuditService audit;

    public AssistantServiceImpl(ChatClient chatClient, HybridRetriever retriever,
                                PromptLibrary prompts, IncidentService incidentService,
                                IncidentRepository incidents, DocumentMapper documentMapper,
                                AssistantProperties properties, AuditService audit) {
        this.chatClient = chatClient;
        this.retriever = retriever;
        this.prompts = prompts;
        this.incidentService = incidentService;
        this.incidents = incidents;
        this.documentMapper = documentMapper;
        this.properties = properties;
        this.audit = audit;
    }

    // ------------------------------------------------------------ knowledge Q&A

    /**
     * Answers a question from the knowledge base.
     *
     * <p>Retrieval is explicit rather than through Spring AI's
     * {@code QuestionAnswerAdvisor}, for two reasons that matter here: the advisor
     * cannot scope retrieval to the caller's clearance, and it does not surface
     * which chunks it used — without which citations cannot be checked.
     */
    @Override
    @Transactional(readOnly = true)
    public AnswerResponse ask(AskRequest request) {
        requireEnabled("The knowledge base is still searchable directly.");
        long started = System.currentTimeMillis();

        List<RetrievedChunk> retrieved = retriever.retrieve(new HybridRetriever.Query(
                request.question(), SecurityUtils.currentClearance(), request.service(),
                properties.getRetrievalTopK(), properties.getMinSimilarity()));

        if (retrieved.isEmpty()) {
            // Saying nothing was found is the honest and useful answer. Falling
            // back to the model's general knowledge would produce plausible
            // instructions for somebody else's system.
            throw new AssistantException(AssistantException.Kind.INSUFFICIENT_CONTEXT,
                    "Nothing in the knowledge base matches that question. It may not be "
                            + "documented, the runbook may not have been uploaded, or it may "
                            + "be above your access level.");
        }

        String context = retrieved.stream()
                .map(c -> "[" + c.reference() + "] (" + c.citation() + ")\n" + c.textForPrompt())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = UntrustedText.instruction() + "\n\nQuestion:\n"
                + UntrustedText.fence("question", request.question(), 4000)
                + "\n\nDocumentation extracts:\n"
                + UntrustedText.fence("documentation", context, 40_000);

        String answer;
        try {
            answer = chatClient.prompt()
                    .system(prompts.get("knowledge-qa"))
                    .user(prompt)
                    .options(ChatOptions.builder()
                            .temperature(properties.getAnalysisTemperature())
                            .build())
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            throw new AssistantException(AssistantException.Kind.PROVIDER_UNAVAILABLE,
                    "The assistant could not answer: " + ex.getMessage(), ex);
        }

        CitationVerifier.Result check = CitationVerifier.verify(answer, retrieved);
        long elapsed = System.currentTimeMillis() - started;

        if (!check.clean()) {
            audit.recordAssistantCall(AuditService.ASSISTANT_ASK, null, prompt, retrieved,
                    null, "REJECTED", check.problems().size(), elapsed);

            // Withheld, not shown with a caveat. An answer citing a document the
            // model was never given came from training data, and for a question
            // about one company's internal procedure that is worse than no answer.
            log.warn("rejected an answer failing citation verification: {}", check.problems());
            throw new AssistantException(AssistantException.Kind.OUTPUT_REJECTED,
                    "The generated answer referenced material that is not in the retrieved "
                            + "sources and has been withheld. The matching documentation "
                            + "sections are still available to read directly.");
        }

        List<String> warnings = new ArrayList<>();
        // Worth flagging even when everything verifies: a chunk holding steps 4 to
        // 7 of a nine-step recovery answers the question and omits the part that
        // makes it safe.
        retrieved.stream()
                .filter(c -> c.chunk().getStepCount() > 0 && !c.chunk().isCompleteProcedure())
                .findFirst()
                .ifPresent(c -> warnings.add("PARTIAL_PROCEDURE: source " + c.citation()
                        + " contains numbered steps that may not be the full procedure; "
                        + "open the document before acting on them"));

        audit.recordAssistantCall(AuditService.ASSISTANT_ASK, null, prompt, retrieved,
                null, "CLEAN", 0, elapsed);

        return new AnswerResponse(answer,
                retrieved.stream().map(documentMapper::toResponse).toList(),
                List.copyOf(warnings));
    }

    // ------------------------------------------------------------------- RCA

    /**
     * Drafts a root cause analysis.
     *
     * <p>Three guards, because the output of this feature becomes the
     * organisation's permanent account of what happened and a fabricated root
     * cause in a postmortem does not look like a fabrication — it looks like a
     * postmortem.
     *
     * <ol>
     *   <li>A floor on evidence. Below it the request is refused outright.</li>
     *   <li>Provenance per finding: observed and cited, or labelled inference.</li>
     *   <li>Verification after generation, which downgrades unsupported claims,
     *       caps unsupported confidence, and withholds a blaming root cause.</li>
     * </ol>
     *
     * <p>Nothing here writes to the incident's root cause field.
     */
    @Override
    @Transactional
    public RcaResponse analyseRootCause(Long incidentId) {
        requireEnabled("Incident management is unaffected.");
        long started = System.currentTimeMillis();

        Incident incident = incidentService.requireIncident(incidentId);
        List<TimelineEntry> timeline = incidentService.evidenceFor(incidentId);
        EvidenceIndex evidence = EvidenceIndex.of(timeline);

        if (evidence.size() < properties.getMinEvidenceEntries()) {
            // Refusing is the correct answer. Asked to explain an incident with
            // two lines on its timeline, a model produces a confident and entirely
            // invented cause, indistinguishable from a real one because it is
            // written in the same register with the same structure.
            throw new AssistantException(AssistantException.Kind.INSUFFICIENT_CONTEXT,
                    "Incident " + incident.getIncidentNumber() + " has " + evidence.size()
                            + " timeline entries that count as evidence. At least "
                            + properties.getMinEvidenceEntries() + " are needed before a root "
                            + "cause analysis can be attempted. Add what was observed and what "
                            + "was tried, then try again.");
        }

        String prompt = UntrustedText.instruction()
                + "\n\nIncident " + incident.getIncidentNumber()
                + "\nService: " + incident.getService()
                + "\nSeverity: " + incident.getSeverity()
                + "\nDetected: " + incident.getDetectedAt()
                + (incident.getMitigatedAt() == null ? ""
                        : "\nMitigated: " + incident.getMitigatedAt())
                + "\n\nTitle and description:\n"
                + UntrustedText.fence("incident-description",
                        incident.getTitle() + "\n\n" + incident.getDescription(),
                        properties.getMaxIncidentCharacters())
                + "\n\nTimeline evidence. Cite these references and no others:\n"
                + UntrustedText.fence("timeline", evidence.forPrompt(),
                        properties.getMaxIncidentCharacters())
                + "\n\nValid evidence references: " + evidence.references();

        RcaDraft raw;
        try {
            raw = chatClient.prompt()
                    .system(prompts.get("rca-analysis"))
                    .user(prompt)
                    .options(ChatOptions.builder()
                            .temperature(properties.getAnalysisTemperature())
                            .build())
                    .call()
                    .entity(RcaDraft.class);
        } catch (RuntimeException ex) {
            throw new AssistantException(AssistantException.Kind.PROVIDER_UNAVAILABLE,
                    "The analysis could not be generated: " + ex.getMessage(), ex);
        }

        if (raw == null) {
            throw new AssistantException(AssistantException.Kind.OUTPUT_REJECTED,
                    "The model returned no usable analysis");
        }

        RcaVerifier.Result verified = RcaVerifier.verify(raw, evidence);
        long elapsed = System.currentTimeMillis() - started;

        audit.recordAssistantCall(AuditService.ASSISTANT_RCA,
                incident.getIncidentNumber(), prompt, null,
                List.copyOf(evidence.references()), verified.outcome(),
                verified.corrections().size(), elapsed);

        // Recorded on the timeline as an ASSISTANT entry, which excludes it from
        // being cited as evidence by a later analysis.
        incidentService.recordAssistantEntry(incidentId,
                "RCA draft generated (" + verified.draft().confidence() + ", "
                        + verified.corrections().size() + " corrections)");

        log.info("RCA drafted for {} with {} evidence entries, {} corrections, confidence {}",
                incident.getIncidentNumber(), evidence.size(), verified.corrections().size(),
                verified.draft().confidence());

        String note = verified.draft().isConclusive()
                ? "This is a draft. Read it against the timeline before recording a root cause."
                : "The timeline does not support a root cause determination. "
                        + "The findings below are still worth reading.";

        return new RcaResponse(verified.draft(), verified.corrections(), evidence.size(),
                List.copyOf(evidence.references()), verified.outcome(),
                verified.draft().isConclusive(), note);
    }

    // ---------------------------------------------------------- precedents

    /**
     * Past incidents whose resolution might apply.
     *
     * <h2>The trap this is built around</h2>
     * Text similarity retrieves incidents with similar <em>symptoms</em>, and
     * symptoms are a poor guide to cause. "Connection pool exhausted" has perhaps
     * fifteen distinct root causes: a slow query, a leaked connection, an
     * undersized pool, a downstream timeout, a failover, a retry storm.
     * Presenting the fix from one of them as "the resolution" leads an engineer to
     * apply a change that is at best irrelevant and at worst harmful, while
     * looking authoritative.
     *
     * <p>So each suggestion leads with the past incident's root cause, the ranking
     * is arithmetic, and no model reorders the list.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SuggestionResponse> suggestPrecedents(Long incidentId) {
        Incident current = incidentService.requireIncident(incidentId);

        List<IncidentRepository.SimilarIncidentRow> rows = incidents.findSimilarResolved(
                current.getTitle() + " " + current.getDescription(),
                current.getId(),
                properties.getSimilarIncidentLimit() * 4);

        List<SuggestionResponse> suggestions = new ArrayList<>();
        double halfLifeDays = Math.max(1, properties.getPrecedentHalfLife().toDays());

        for (var row : rows) {
            double lexical = row.getSimilarity() == null ? 0.0 : row.getSimilarity();
            double ageDays = row.getResolvedAt() == null ? halfLifeDays
                    : Math.max(0, Duration.between(row.getResolvedAt(), Instant.now()).toDays());
            // Exponential decay: infrastructure changes, and a two-year-old fix
            // often refers to a system that no longer exists.
            double recency = Math.pow(0.5, ageDays / halfLifeDays);

            double score = lexical * recency;
            List<String> reasons = new ArrayList<>();
            List<String> cautions = new ArrayList<>();

            reasons.add(String.format("text similarity %.2f", lexical));
            reasons.add(String.format("resolved %.0f days ago (recency factor %.2f)",
                    ageDays, recency));

            if (current.getService() != null && current.getService().equals(row.getService())) {
                score *= SAME_SERVICE_MULTIPLIER;
                reasons.add("same service");
            } else {
                cautions.add("a different service (" + row.getService()
                        + "); the same symptom often has a different cause elsewhere");
            }

            if (current.getSeverity().name().equals(row.getSeverity())) {
                score *= SAME_SEVERITY_MULTIPLIER;
                reasons.add("same severity");
            }

            if (row.getRootCause() == null || row.getRootCause().isBlank()) {
                cautions.add("no root cause was recorded, so there is no way to tell whether "
                        + "this resolution addresses the same underlying problem");
            }
            if (ageDays > halfLifeDays * 2) {
                cautions.add("over " + Math.round(ageDays) + " days old; the system it "
                        + "describes may have changed");
            }

            suggestions.add(new SuggestionResponse(row.getIncidentNumber(), row.getTitle(),
                    row.getService(), row.getRootCause(), row.getResolutionSummary(),
                    row.getResolvedAt(), score, lexical, recency,
                    List.copyOf(reasons), List.copyOf(cautions)));
        }

        suggestions.sort(Comparator.comparingDouble(SuggestionResponse::score).reversed());
        return suggestions.stream().limit(properties.getSimilarIncidentLimit()).toList();
    }

    // ------------------------------------------------------------- drafting

    @Override
    @Transactional
    public String summarise(Long incidentId) {
        requireEnabled("Incident management is unaffected.");
        Incident incident = incidentService.requireIncident(incidentId);

        if (incident.getDescription() == null
                || incident.getDescription().length() < MIN_LENGTH_TO_SUMMARISE) {
            // Summarising something already short loses information for nothing.
            return incident.getDescription();
        }

        String summary = generate("incident-summary",
                "Incident description:\n" + UntrustedText.fence("incident-description",
                        incident.getDescription(), properties.getMaxIncidentCharacters()),
                properties.getAnalysisTemperature());

        // Stored beside the original, never replacing it. Nothing else in the
        // system reads it: a root cause analysis built on a summary is an analysis
        // of an analysis, and the detail a summary drops is occasionally the one
        // that mattered.
        incident.setAiSummary(summary);
        incidents.save(incident);
        return summary;
    }

    /**
     * Drafts a customer update.
     *
     * <p>Written from the incident record, which is full of material customers
     * must not see. The prompt asks for none of it; {@link Redactor} then removes
     * it anyway, because a prompt is advisory and a regular expression is not.
     */
    @Override
    @Transactional(readOnly = true)
    public DraftResponse draftCustomerUpdate(Long incidentId, String audience) {
        requireEnabled("Incident management is unaffected.");
        long started = System.currentTimeMillis();
        Incident incident = incidentService.requireIncident(incidentId);

        String prompt = "Incident status: " + incident.getStatus()
                + "\nSeverity: " + incident.getSeverity().getLabel()
                + "\nCustomer impacting: " + incident.isCustomerImpacting()
                + "\nDetected: " + incident.getDetectedAt()
                + (incident.getMitigatedAt() == null ? "\nNot yet mitigated."
                        : "\nMitigated: " + incident.getMitigatedAt())
                + "\nAudience: " + (audience == null ? "all affected customers" : audience)
                + "\n\nInternal record:\n"
                + UntrustedText.fence("incident",
                        incident.getTitle() + "\n\n" + incident.getDescription(),
                        properties.getMaxIncidentCharacters());

        String raw = generate("customer-update", prompt, properties.getDraftingTemperature());

        Redactor.Result redaction = Redactor.redact(raw);
        List<String> commitments = Redactor.findCommitments(redaction.text());

        if (!redaction.isClean()) {
            log.info("redacted {} internal references from a customer draft for {}",
                    redaction.redactions().size(), incident.getIncidentNumber());
        }

        audit.recordAssistantCall(AuditService.ASSISTANT_DRAFT,
                incident.getIncidentNumber(), prompt, null, null,
                redaction.isClean() ? "CLEAN" : "CORRECTED",
                redaction.redactions().size(), System.currentTimeMillis() - started);

        String subject = "Service update: " + incident.getService()
                + (incident.getStatus().isOpen() ? " — ongoing" : " — resolved");

        return new DraftResponse(subject, redaction.text(),
                redaction.redactions().stream()
                        .map(r -> r.kind() + ": " + r.original()).toList(),
                // Flagged, not removed. A restoration-time commitment is a
                // contractual statement and belongs in front of the person
                // accountable for it, not quietly deleted behind their back.
                commitments, true);
    }

    /**
     * An internal post-incident report.
     *
     * <p>No redaction: the detail is the point. Still a draft, because a report
     * nobody edited is a report nobody stands behind.
     */
    @Override
    @Transactional(readOnly = true)
    public DraftResponse draftPostIncidentReport(Long incidentId) {
        requireEnabled("Incident management is unaffected.");
        Incident incident = incidentService.requireIncident(incidentId);

        String timelineText = incidentService.evidenceFor(incidentId).stream()
                .map(e -> e.getOccurredAt() + " (" + e.getEntryType() + ") " + e.getContent())
                .collect(Collectors.joining("\n"));

        String body = generate("post-incident-report",
                "Incident " + incident.getIncidentNumber()
                        + "\nService: " + incident.getService()
                        + "\nSeverity: " + incident.getSeverity()
                        + "\nTime to acknowledge: " + incident.timeToAcknowledge()
                        + "\nTime to mitigate: " + incident.timeToMitigate()
                        + "\nTime to resolve: " + incident.timeToResolve()
                        + "\nRecorded root cause: "
                        + (incident.getRootCause() == null ? "none recorded"
                                : incident.getRootCause())
                        + "\n\nDescription:\n"
                        + UntrustedText.fence("description", incident.getDescription(),
                                properties.getMaxIncidentCharacters())
                        + "\n\nTimeline:\n"
                        + UntrustedText.fence("timeline", timelineText,
                                properties.getMaxIncidentCharacters()),
                properties.getDraftingTemperature());

        return new DraftResponse("Post-incident report: " + incident.getIncidentNumber(),
                body, List.of(), Redactor.findCommitments(body), true);
    }

    // ------------------------------------------------------------ internals

    private String generate(String promptName, String user, double temperature) {
        try {
            return chatClient.prompt()
                    .system(prompts.get(promptName))
                    .user(UntrustedText.instruction() + "\n\n" + user)
                    .options(ChatOptions.builder().temperature(temperature).build())
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            throw new AssistantException(AssistantException.Kind.PROVIDER_UNAVAILABLE,
                    "Could not produce the draft: " + ex.getMessage(), ex);
        }
    }

    private void requireEnabled(String stillWorks) {
        if (!properties.isEnabled()) {
            throw new AssistantException(AssistantException.Kind.PROVIDER_UNAVAILABLE,
                    "The assistant is disabled. " + stillWorks);
        }
    }
}
