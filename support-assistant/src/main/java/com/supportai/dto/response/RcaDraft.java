package com.supportai.dto.response;

import java.util.List;

/**
 * A root cause analysis draft.
 *
 * <h2>The shape of this record is the safety mechanism</h2>
 * A free-text RCA from a language model is fluent, well-organised, and gives no
 * way to tell which parts are things that happened from which are the model's
 * guesses. Once pasted into a postmortem it becomes the organisation's account of
 * the incident, and the guesses are indistinguishable from the facts.
 *
 * <p>So every finding is classified and must point at its evidence, and
 * {@link Confidence#INSUFFICIENT_EVIDENCE} is a first-class outcome rather than a
 * failure. Most incidents genuinely do not have enough recorded to determine a
 * cause, and a system with no way to say so will manufacture one every time.
 */
public record RcaDraft(String summary,
                       List<Finding> findings,
                       String rootCauseStatement,
                       List<String> contributingFactors,
                       List<String> correctiveActions,
                       Confidence confidence,
                       List<String> missingEvidence) {

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW,
        /** The timeline does not support any root cause determination. */
        INSUFFICIENT_EVIDENCE
    }

    public enum Classification {
        /** Directly supported by a timeline entry. Must cite one. */
        OBSERVED,
        /** The model's reasoning from observations. Not itself a fact. */
        INFERRED
    }

    public record Finding(String statement, Classification classification,
                          List<String> evidenceRefs) {

        public Finding {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public RcaDraft {
        findings = findings == null ? List.of() : List.copyOf(findings);
        contributingFactors = contributingFactors == null
                ? List.of() : List.copyOf(contributingFactors);
        correctiveActions = correctiveActions == null
                ? List.of() : List.copyOf(correctiveActions);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
    }

    public boolean isConclusive() {
        return confidence != Confidence.INSUFFICIENT_EVIDENCE && rootCauseStatement != null
                && !rootCauseStatement.isBlank();
    }

    public long observedCount() {
        return findings.stream()
                .filter(f -> f.classification() == Classification.OBSERVED).count();
    }
}
