package com.supportai.validation;

import com.supportai.dto.response.RcaDraft;
import com.supportai.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Post-processes an RCA draft before anyone sees it.
 *
 * <p>Four corrections, each for a failure that is easy to produce and nearly
 * impossible to spot in fluent prose. The output of this feature becomes the
 * organisation's permanent account of an incident: a fabricated root cause in a
 * postmortem does not look like a fabrication, it looks like a postmortem.
 */
public final class RcaVerifier {

    private static final Logger log = LoggerFactory.getLogger(RcaVerifier.class);

    /**
     * Phrases attributing cause to a person rather than to a system.
     *
     * <p>Blameless postmortems are not politeness. An RCA naming an individual
     * changes what people write down during the next incident — and the timeline
     * is the only evidence this entire feature runs on. Protecting the timeline is
     * protecting the input.
     */
    private static final List<Pattern> BLAME = List.of(
            Pattern.compile("(?i)\\b(?:due to|because of|caused by|owing to)\\s+"
                    + "(?:an?\\s+)?(?:engineer|developer|operator|admin(?:istrator)?|"
                    + "technician|analyst)\\b"),
            Pattern.compile("(?i)\\b(?:human|operator|engineer)\\s+error\\b"),
            Pattern.compile("(?i)\\b(?:failed|forgot|neglected|omitted)\\s+to\\s+\\w+"
                    + "[^.]{0,40}\\bby\\s+[A-Z][a-z]+\\b"));

    private RcaVerifier() {
    }

    public record Result(RcaDraft draft, List<String> corrections) {

        public boolean wasCorrected() {
            return !corrections.isEmpty();
        }

        /** CLEAN, CORRECTED or REJECTED, for the audit row. */
        public String outcome() {
            if (draft.confidence() == RcaDraft.Confidence.INSUFFICIENT_EVIDENCE) {
                return "REJECTED";
            }
            return corrections.isEmpty() ? "CLEAN" : "CORRECTED";
        }
    }

    public static Result verify(RcaDraft draft, EvidenceIndex evidence) {
        List<String> corrections = new ArrayList<>();
        List<RcaDraft.Finding> checked = new ArrayList<>();

        for (RcaDraft.Finding finding : draft.findings()) {
            List<String> valid = finding.evidenceRefs().stream()
                    .filter(evidence::contains)
                    .toList();

            List<String> invented = finding.evidenceRefs().stream()
                    .filter(ref -> !evidence.contains(ref))
                    .toList();
            if (!invented.isEmpty()) {
                corrections.add("finding cited non-existent evidence " + invented
                        + ": \"" + TextUtil.truncate(TextUtil.flatten(finding.statement()), 90)
                        + "\"");
            }

            // A finding presented as observed but citing nothing is a hypothesis
            // wearing the clothes of a fact. Downgraded rather than dropped: it may
            // well be right, and it should be labelled as a guess.
            if (finding.classification() == RcaDraft.Classification.OBSERVED && valid.isEmpty()) {
                corrections.add("downgraded to INFERRED, no supporting evidence: \""
                        + TextUtil.truncate(TextUtil.flatten(finding.statement()), 90) + "\"");
                checked.add(new RcaDraft.Finding(finding.statement(),
                        RcaDraft.Classification.INFERRED, List.of()));
            } else {
                checked.add(new RcaDraft.Finding(finding.statement(),
                        finding.classification(), valid));
            }
        }

        RcaDraft.Confidence confidence = draft.confidence();
        String rootCause = draft.rootCauseStatement();

        long observed = checked.stream()
                .filter(f -> f.classification() == RcaDraft.Classification.OBSERVED).count();

        // A confident conclusion resting on no observations is the exact output
        // this feature must not produce.
        if (observed == 0 && confidence != RcaDraft.Confidence.INSUFFICIENT_EVIDENCE) {
            corrections.add("confidence reduced to INSUFFICIENT_EVIDENCE: no finding is "
                    + "supported by a timeline entry");
            confidence = RcaDraft.Confidence.INSUFFICIENT_EVIDENCE;
        } else if (observed <= 1 && confidence == RcaDraft.Confidence.HIGH) {
            corrections.add("confidence reduced from HIGH to MEDIUM: only " + observed
                    + " observed finding");
            confidence = RcaDraft.Confidence.MEDIUM;
        }

        if (rootCause != null && !rootCause.isBlank()) {
            for (Pattern pattern : BLAME) {
                if (pattern.matcher(rootCause).find()) {
                    corrections.add("root cause statement withheld: it attributes the "
                            + "incident to a person rather than to a system or process");
                    rootCause = null;
                    confidence = RcaDraft.Confidence.LOW;
                    break;
                }
            }
        }

        if (confidence == RcaDraft.Confidence.INSUFFICIENT_EVIDENCE) {
            rootCause = null;
        }

        RcaDraft corrected = new RcaDraft(draft.summary(), checked, rootCause,
                draft.contributingFactors(), draft.correctiveActions(), confidence,
                draft.missingEvidence());

        if (!corrections.isEmpty()) {
            log.info("RCA draft corrected in {} places before presentation", corrections.size());
        }
        return new Result(corrected, List.copyOf(corrections));
    }

    /** Exposed for testing the blame patterns directly. */
    static boolean attributesBlame(String text) {
        return text != null && BLAME.stream().anyMatch(p -> p.matcher(text).find());
    }
}
