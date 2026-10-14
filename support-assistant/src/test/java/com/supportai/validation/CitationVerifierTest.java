package com.supportai.validation;

import com.supportai.entity.Classification;
import com.supportai.entity.DocumentChunk;
import com.supportai.entity.KbDocument;
import com.supportai.retrieval.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationVerifierTest {

    private static final String CONTENT =
            "Restart the payments pods with kubectl rollout restart deploy/payments "
                    + "and verify pool metrics recover within three minutes.";

    private List<RetrievedChunk> retrieved() {
        DocumentChunk chunk = DocumentChunk.builder()
                .id(42L).documentId(1L).ordinal(0)
                .breadcrumb("Payments Runbook > 3.2 Recovery Procedure")
                .content(CONTENT).charCount(CONTENT.length()).build();

        KbDocument document = KbDocument.builder()
                .id(1L).title("Payments Runbook").slug("payments-runbook").versionNumber(3)
                .classification(Classification.INTERNAL).build();

        return List.of(new RetrievedChunk(chunk, document, 0.9, Map.of("lexical", 1), true));
    }

    @Test
    @DisplayName("citing a retrieved chunk passes")
    void acceptsValidCitation() {
        var result = CitationVerifier.verify(
                "Restart the pods and check the metrics [chunk:42].", retrieved());

        assertThat(result.clean()).isTrue();
        assertThat(result.validCitations()).containsExactly(42L);
    }

    @Test
    @DisplayName("citing a chunk that was never retrieved is caught")
    void rejectsUnknownCitation() {
        // The model answering from training data rather than the supplied
        // documentation, with a citation that looks entirely plausible.
        var result = CitationVerifier.verify(
                "Follow the standard procedure [chunk:999].", retrieved());

        assertThat(result.clean()).isFalse();
        assertThat(result.problems()).anyMatch(p -> p.kind().equals("UNKNOWN_CITATION"));
    }

    @Test
    @DisplayName("an exact quote is accepted")
    void acceptsExactQuote() {
        var result = CitationVerifier.verify(
                "The runbook says \"kubectl rollout restart deploy/payments\" [chunk:42].",
                retrieved());

        assertThat(result.clean()).isTrue();
    }

    @Test
    @DisplayName("a near-quote with an altered command is caught")
    void rejectsAlteredQuote() {
        // The dangerous one. It reads as authoritative, gets pasted into a
        // terminal, and the flag that changed is not one anyone notices.
        var result = CitationVerifier.verify(
                "Run \"kubectl rollout restart deploy/payments-api\" to recover [chunk:42].",
                retrieved());

        assertThat(result.clean()).isFalse();
        assertThat(result.problems()).anyMatch(p -> p.kind().equals("UNSUPPORTED_QUOTE"));
    }

    @Test
    @DisplayName("whitespace differences alone are not treated as fabrication")
    void toleratesWhitespaceDifferences() {
        // Line wrapping differs between the stored chunk and the model's
        // rendering of it, and that difference is not a fabrication.
        var result = CitationVerifier.verify(
                "It says \"kubectl   rollout\nrestart deploy/payments\" [chunk:42].",
                retrieved());

        assertThat(result.clean()).isTrue();
    }

    @Test
    @DisplayName("punctuation is preserved, so a changed separator is still caught")
    void doesNotNormaliseAwayPunctuation() {
        // "deploy/payments" and "deploy payments" are different things.
        // Normalising punctuation would make them compare equal.
        var result = CitationVerifier.verify(
                "Use \"kubectl rollout restart deploy payments\" first [chunk:42].",
                retrieved());

        assertThat(result.clean()).isFalse();
    }

    @Test
    @DisplayName("very short quoted fragments are not checked")
    void ignoresShortFragments() {
        // Checking "pool" produces constant false positives and trains people to
        // ignore the warning, which costs more than it catches.
        var result = CitationVerifier.verify("Check the \"pool\" metrics [chunk:42].",
                retrieved());

        assertThat(result.clean()).isTrue();
    }

    @Test
    void handlesEmptyAnswer() {
        assertThat(CitationVerifier.verify("", retrieved()).clean()).isTrue();
        assertThat(CitationVerifier.verify(null, retrieved()).clean()).isTrue();
    }
}
