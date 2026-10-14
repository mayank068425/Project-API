package com.supportai.validation;

import com.supportai.dto.response.RcaDraft;
import com.supportai.entity.TimelineEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RcaVerifierTest {

    private TimelineEntry entry(String content, TimelineEntry.EntrySource source) {
        return TimelineEntry.builder()
                .incidentId(1L)
                .occurredAt(Instant.now())
                .entryType(TimelineEntry.EntryType.OBSERVATION)
                .source(source)
                .content(content)
                .build();
    }

    private EvidenceIndex evidence() {
        return EvidenceIndex.of(List.of(
                entry("Pool saturated at 50/50 with 180 pending", TimelineEntry.EntrySource.HUMAN),
                entry("Downstream provider p99 at 28s", TimelineEntry.EntrySource.HUMAN),
                entry("No read timeout configured on the client",
                        TimelineEntry.EntrySource.HUMAN)));
    }

    private RcaDraft draft(List<RcaDraft.Finding> findings, String rootCause,
                           RcaDraft.Confidence confidence) {
        return new RcaDraft("summary", findings, rootCause, List.of(), List.of(), confidence,
                List.of());
    }

    @Test
    @DisplayName("assistant-written timeline entries are not evidence")
    void assistantEntriesAreExcluded() {
        // An analysis that treats its own earlier output as an observation
        // compounds its errors until nothing traces back to what happened.
        EvidenceIndex index = EvidenceIndex.of(List.of(
                entry("Human observation", TimelineEntry.EntrySource.HUMAN),
                entry("Earlier AI summary", TimelineEntry.EntrySource.ASSISTANT),
                entry("System alert", TimelineEntry.EntrySource.SYSTEM)));

        assertThat(index.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("a citation to evidence that does not exist is stripped")
    void stripsInventedEvidence() {
        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("The pool was saturated",
                        RcaDraft.Classification.OBSERVED, List.of("E1", "E9"))),
                "Missing read timeout on the downstream client",
                RcaDraft.Confidence.MEDIUM), evidence());

        assertThat(result.wasCorrected()).isTrue();
        assertThat(result.draft().findings().get(0).evidenceRefs()).containsExactly("E1");
        assertThat(result.corrections()).anyMatch(c -> c.contains("E9"));
    }

    @Test
    @DisplayName("an observed finding citing nothing is downgraded to inferred")
    void downgradesUnsupportedObservations() {
        // A hypothesis wearing the clothes of a fact. Downgraded rather than
        // deleted: it may well be right, and it should be labelled as a guess.
        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("A retry storm amplified the load",
                        RcaDraft.Classification.OBSERVED, List.of()),
                new RcaDraft.Finding("Pool saturated",
                        RcaDraft.Classification.OBSERVED, List.of("E1"))),
                "Missing timeout", RcaDraft.Confidence.MEDIUM), evidence());

        assertThat(result.draft().findings().get(0).classification())
                .isEqualTo(RcaDraft.Classification.INFERRED);
        assertThat(result.corrections()).anyMatch(c -> c.contains("downgraded"));
    }

    @Test
    @DisplayName("no observed findings forces INSUFFICIENT_EVIDENCE and withdraws the cause")
    void refusesToConcludeWithoutObservations() {
        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("Probably a memory leak",
                        RcaDraft.Classification.INFERRED, List.of())),
                "A memory leak in the connection pool", RcaDraft.Confidence.HIGH), evidence());

        assertThat(result.draft().confidence())
                .isEqualTo(RcaDraft.Confidence.INSUFFICIENT_EVIDENCE);
        assertThat(result.draft().rootCauseStatement()).isNull();
        assertThat(result.draft().isConclusive()).isFalse();
        assertThat(result.outcome()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("HIGH confidence on a single observation is capped at MEDIUM")
    void capsOverconfidence() {
        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("Pool saturated",
                        RcaDraft.Classification.OBSERVED, List.of("E1"))),
                "Missing read timeout", RcaDraft.Confidence.HIGH), evidence());

        assertThat(result.draft().confidence()).isEqualTo(RcaDraft.Confidence.MEDIUM);
    }

    @Test
    @DisplayName("a root cause blaming a person is withheld")
    void withholdsBlame() {
        // Blameless postmortems are not politeness. Naming an individual changes
        // what people write on the next timeline, and the timeline is the only
        // evidence this whole feature runs on.
        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("Pool saturated",
                        RcaDraft.Classification.OBSERVED, List.of("E1")),
                new RcaDraft.Finding("No timeout configured",
                        RcaDraft.Classification.OBSERVED, List.of("E3"))),
                "The outage was caused by an engineer deploying without review",
                RcaDraft.Confidence.HIGH), evidence());

        assertThat(result.draft().rootCauseStatement()).isNull();
        assertThat(result.draft().confidence()).isEqualTo(RcaDraft.Confidence.LOW);
        assertThat(result.corrections()).anyMatch(c -> c.contains("attributes the incident"));
    }

    @Test
    @DisplayName("a systemic root cause is left intact")
    void allowsSystemicCauses() {
        String cause = "The HTTP client had no read timeout, so a slow downstream held pool "
                + "connections indefinitely.";

        var result = RcaVerifier.verify(draft(List.of(
                new RcaDraft.Finding("Pool saturated",
                        RcaDraft.Classification.OBSERVED, List.of("E1")),
                new RcaDraft.Finding("Downstream slow",
                        RcaDraft.Classification.OBSERVED, List.of("E2")),
                new RcaDraft.Finding("No timeout configured",
                        RcaDraft.Classification.OBSERVED, List.of("E3"))),
                cause, RcaDraft.Confidence.HIGH), evidence());

        assertThat(result.draft().rootCauseStatement()).isEqualTo(cause);
        assertThat(result.draft().confidence()).isEqualTo(RcaDraft.Confidence.HIGH);
        assertThat(result.outcome()).isEqualTo("CLEAN");
    }
}
