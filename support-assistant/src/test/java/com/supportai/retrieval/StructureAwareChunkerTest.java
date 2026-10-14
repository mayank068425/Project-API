package com.supportai.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chunking behaviour that keeps a runbook safe to act on.
 */
class StructureAwareChunkerTest {

    private static final String RUNBOOK = String.join("\n",
            "## 3.2 Recovery Procedure",
            "1. Confirm the primary is healthy.",
            "2. Drain the connection pool.",
            "3. DO NOT restart the application before completing step 4 - restarting",
            "   first loses the in-flight transaction log.",
            "4. Flush the transaction log to durable storage.",
            "5. Confirm the flush completed.",
            "6. Restart the payments pods: kubectl rollout restart deploy/payments",
            "7. Verify pool metrics recover within 3 minutes.",
            "",
            "Cache problems have their own section below.",
            "",
            "4. Cache Eviction Storms",
            "This section describes what to do when the cache evicts too aggressively.");

    @Test
    @DisplayName("the restart command is never separated from its warning")
    void restartStaysWithItsWarning() {
        // The failure this whole class exists to prevent. A chunk containing the
        // restart command without step 3's warning retrieves beautifully for "how
        // do I restart the payments pods" and tells an engineer to destroy data.
        List<StructureAwareChunker.Chunk> chunks =
                StructureAwareChunker.chunk("Payments Runbook", RUNBOOK);

        StructureAwareChunker.Chunk withRestart = chunks.stream()
                .filter(c -> c.content().contains("kubectl rollout restart"))
                .findFirst().orElseThrow();

        assertThat(withRestart.content()).contains("DO NOT restart the application");
    }

    @Test
    @DisplayName("a numbered step is not mistaken for a heading")
    void numberedStepIsNotAHeading() {
        // "6. Restart the payments pods" matches every reasonable numbered-heading
        // pattern: capital letter, no terminal punctuation. The signal that it is
        // a step is in its neighbours, not in the line itself.
        boolean[] steps = StructureAwareChunker.detectStepRuns(RUNBOOK.split("\\R"));
        String[] lines = RUNBOOK.split("\\R");

        int restartLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("kubectl rollout restart")) {
                restartLine = i;
            }
        }

        assertThat(restartLine).isGreaterThan(0);
        assertThat(steps[restartLine]).isTrue();
    }

    @Test
    @DisplayName("a lone numbered line stays a heading")
    void loneNumberedLineIsAHeading() {
        // "4. Cache Eviction Storms" has no run around it, so it is a section
        // heading rather than step four of something.
        boolean[] steps = StructureAwareChunker.detectStepRuns(RUNBOOK.split("\\R"));
        String[] lines = RUNBOOK.split("\\R");

        int headingLine = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().equals("4. Cache Eviction Storms")) {
                headingLine = i;
            }
        }

        assertThat(headingLine).isGreaterThan(0);
        assertThat(steps[headingLine]).isFalse();
    }

    @Test
    @DisplayName("the breadcrumb is embedded with the content")
    void breadcrumbIsEmbedded() {
        // "1. Confirm the primary is healthy" embeds almost identically whichever
        // runbook it came from. The heading path is what separates them.
        List<StructureAwareChunker.Chunk> chunks =
                StructureAwareChunker.chunk("Payments Runbook", RUNBOOK);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).embeddedText()).contains("Payments Runbook");
    }

    @Test
    @DisplayName("an empty document produces no chunks rather than one empty chunk")
    void emptyDocumentProducesNothing() {
        assertThat(StructureAwareChunker.chunk("Title", "")).isEmpty();
        assertThat(StructureAwareChunker.chunk("Title", null)).isEmpty();
    }
}
