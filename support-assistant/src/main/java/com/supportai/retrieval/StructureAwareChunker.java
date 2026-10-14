package com.supportai.retrieval;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a document into retrievable chunks along its own structure.
 *
 * <h2>What fixed-size chunking does to a runbook</h2>
 * Sliding a 400-character window over a recovery procedure can produce a chunk
 * that reads:
 *
 * <pre>
 * : `kubectl rollout restart deploy/payments`.
 * 7. Verify pool metrics recover within 3 minutes.
 * ### 3.3 Escalation
 * </pre>
 *
 * <p>No heading. No procedure. And critically, no step 3 — the step that said
 * <em>do not restart before completing step 4, restarting first loses the
 * in-flight transaction log</em>. That chunk is the top hit for "how do I restart
 * the payments pods", and an answer built from it tells an engineer to destroy
 * data. The damage happens at ingestion and surfaces mid-incident.
 *
 * <h2>Rules</h2>
 * Split on headings. Never split a numbered procedure — two or more consecutive
 * numbered steps are emitted whole, even if that exceeds the target size, because
 * an oversized chunk is a performance problem and a split procedure is a safety
 * one. Stamp the heading breadcrumb onto every chunk and embed it with the text.
 *
 * <h2>The subtle part: a numbered heading and a numbered step look identical</h2>
 * {@code 6. Restart the payments pods} matches every reasonable
 * numbered-<em>heading</em> pattern — capital letter, no terminal punctuation —
 * so a naive detector promotes step 6 to a heading and splits the procedure at
 * the worst possible point.
 *
 * <p>In isolation the two are genuinely indistinguishable. The signal is in the
 * neighbours: steps come in runs of consecutive incrementing numbers, headings do
 * not. Detection is therefore two-pass — find the runs first, exempt them, then
 * classify what remains. That keeps {@code 4. Cache Eviction Storms} a heading
 * while {@code 6. Restart the payments pods} stays a step.
 */
public final class StructureAwareChunker {

    /** Target size. A procedure may exceed it; see the class comment. */
    private static final int TARGET_CHARS = 1200;
    private static final int MAX_BREADCRUMB_DEPTH = 3;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern NUMBERED_HEADING =
            Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?\\s+([A-Z][^.!?]{2,80})$");
    private static final Pattern NUMBERED_STEP = Pattern.compile("^(\\d+)[.)]\\s+(.+)$");

    private StructureAwareChunker() {
    }

    public record Chunk(String breadcrumb, String content, int stepCount,
                        boolean completeProcedure) {

        public int charCount() {
            return content.length();
        }

        /** Breadcrumb plus content. This is what gets embedded. */
        public String embeddedText() {
            return (breadcrumb == null || breadcrumb.isBlank() ? "" : breadcrumb + "\n") + content;
        }
    }

    public static List<Chunk> chunk(String documentTitle, String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String[] lines = text.split("\\R");
        boolean[] isStep = detectStepRuns(lines);

        Deque<String> headings = new ArrayDeque<>();
        if (documentTitle != null && !documentTitle.isBlank()) {
            headings.addLast(documentTitle);
        }

        List<String> buffer = new ArrayList<>();
        int bufferSteps = 0;
        boolean inProcedure = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // A step never opens a new section, whatever it looks like.
            if (!isStep[i]) {
                String heading = headingText(line);
                if (heading != null) {
                    if (!buffer.isEmpty()) {
                        chunks.add(build(headings, buffer, bufferSteps, !inProcedure || true));
                        buffer.clear();
                        bufferSteps = 0;
                    }
                    pushHeading(headings, line, heading);
                    inProcedure = false;
                    continue;
                }
            }

            if (isStep[i]) {
                inProcedure = true;
                bufferSteps++;
            }
            buffer.add(line);

            // Only ever break outside a procedure. Inside one the chunk grows
            // past the target rather than losing a step.
            int size = buffer.stream().mapToInt(String::length).sum();
            boolean nextIsStep = i + 1 < lines.length && isStep[i + 1];
            if (size >= TARGET_CHARS && !nextIsStep && !isStep[i]) {
                chunks.add(build(headings, buffer, bufferSteps, true));
                buffer.clear();
                bufferSteps = 0;
                inProcedure = false;
            }
        }

        if (!buffer.isEmpty()) {
            chunks.add(build(headings, buffer, bufferSteps, true));
        }
        return chunks;
    }

    /**
     * Marks lines belonging to a run of consecutive incrementing numbers.
     *
     * <p>A run of two or more is a procedure. A single numbered line standing
     * alone is a heading, which is why {@code 4. Cache Eviction Storms} between
     * two prose paragraphs is not treated as a step.
     */
    static boolean[] detectStepRuns(String[] lines) {
        boolean[] isStep = new boolean[lines.length];
        RunState run = new RunState();

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = NUMBERED_STEP.matcher(lines[i].strip());
            if (matcher.matches()) {
                int number = Integer.parseInt(matcher.group(1));
                if (run.isOpen() && number == run.expected) {
                    run.expected = number + 1;
                    continue;
                }
                // Close the previous run before starting a new one.
                run.close(isStep, i);
                run.open(i, number);
            } else if (!lines[i].isBlank() && !isContinuation(lines[i])) {
                // A blank line does not end a procedure and neither does an
                // indented continuation; prose does. Steps are routinely
                // separated by blank lines and carry their own sub-bullets.
                run.close(isStep, i);
            }
        }
        run.close(isStep, lines.length);
        return isStep;
    }

    /**
     * Tracks a candidate run of consecutive numbered lines.
     *
     * <p>A run is only a procedure once it holds two or more steps. That
     * threshold is the whole distinction between {@code 6. Restart the payments
     * pods} (a step, because 5 and 7 sit around it) and {@code 4. Cache Eviction
     * Storms} (a heading, because nothing continues it).
     */
    private static final class RunState {
        private int start = -1;
        private int first = -1;
        private int expected = -1;

        boolean isOpen() {
            return start >= 0;
        }

        void open(int index, int number) {
            start = index;
            first = number;
            expected = number + 1;
        }

        void close(boolean[] isStep, int end) {
            if (start >= 0 && expected - first >= 2) {
                for (int j = start; j < end; j++) {
                    isStep[j] = true;
                }
            }
            start = -1;
            first = -1;
            expected = -1;
        }
    }

    /** Indented text or a sub-bullet belongs to the step above it. */
    private static boolean isContinuation(String line) {
        return line.startsWith("  ") || line.startsWith("\t")
                || line.strip().startsWith("-") || line.strip().startsWith("*")
                || line.strip().startsWith("```");
    }

    private static String headingText(String line) {
        String stripped = line.strip();
        Matcher markdown = MARKDOWN_HEADING.matcher(stripped);
        if (markdown.matches()) {
            return markdown.group(2).strip();
        }
        Matcher numbered = NUMBERED_HEADING.matcher(stripped);
        if (numbered.matches()) {
            return stripped;
        }
        return null;
    }

    private static void pushHeading(Deque<String> headings, String rawLine, String heading) {
        Matcher markdown = MARKDOWN_HEADING.matcher(rawLine.strip());
        int level = markdown.matches() ? markdown.group(1).length() : 2;

        // Keep the document title, then trim to the heading's depth.
        while (headings.size() > Math.max(1, level - 1)) {
            headings.removeLast();
        }
        headings.addLast(heading);
    }

    private static Chunk build(Deque<String> headings, List<String> buffer, int steps,
                               boolean complete) {
        List<String> trail = new ArrayList<>(headings);
        // Deep hierarchies produce breadcrumbs longer than the chunk. Keeping the
        // document title and the two nearest headings is what a reader needs to
        // know where they are.
        if (trail.size() > MAX_BREADCRUMB_DEPTH) {
            List<String> trimmed = new ArrayList<>();
            trimmed.add(trail.get(0));
            trimmed.addAll(trail.subList(trail.size() - (MAX_BREADCRUMB_DEPTH - 1), trail.size()));
            trail = trimmed;
        }
        return new Chunk(String.join(" > ", trail),
                String.join("\n", buffer).strip(), steps, complete);
    }
}
