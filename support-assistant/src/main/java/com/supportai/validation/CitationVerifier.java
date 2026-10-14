package com.supportai.validation;

import com.supportai.retrieval.RetrievedChunk;
import com.supportai.util.Constants;
import com.supportai.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks that an answer cites only chunks it was shown, and quotes only text
 * those chunks contain.
 *
 * <p>Two distinct failures, needing separate checks.
 *
 * <p><b>Citing something that was not retrieved.</b> The model read a great deal
 * of documentation during training and will occasionally answer from that
 * instead, attaching a citation that looks entirely plausible. For a question
 * about one company's internal runbook, an answer drawn from generic knowledge is
 * worse than no answer: it is wrong in a way that is specific, confident, and
 * formatted identically to a correct answer.
 *
 * <p><b>Quoting text the source does not contain.</b> The more insidious of the
 * two. A command with one flag altered, or a threshold changed from 300 to 3000,
 * reads as authoritative and gets pasted into a terminal.
 */
public final class CitationVerifier {

    private static final Logger log = LoggerFactory.getLogger(CitationVerifier.class);

    private static final Pattern CITATION =
            Pattern.compile(Pattern.quote(Constants.CITATION_PREFIX) + "(\\d+)");

    /**
     * Quoted spans worth checking.
     *
     * <p>The 12-character floor keeps ordinary quoted words out of it. Checking
     * {@code "pool"} against the sources produces constant false positives and
     * trains people to ignore the warning, which costs more than it catches.
     */
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]{12,400})\"");

    private CitationVerifier() {
    }

    public record Problem(String kind, String detail) {
    }

    public record Result(boolean clean, List<Long> validCitations, List<Problem> problems) {

        public boolean hasCitations() {
            return !validCitations.isEmpty();
        }
    }

    public static Result verify(String answer, List<RetrievedChunk> retrieved) {
        List<Problem> problems = new ArrayList<>();
        Set<Long> valid = new LinkedHashSet<>();

        if (answer == null || answer.isBlank()) {
            return new Result(true, List.of(), List.of());
        }

        Map<Long, RetrievedChunk> available = retrieved.stream()
                .collect(Collectors.toMap(c -> c.chunk().getId(), Function.identity(),
                        (a, b) -> a));

        Matcher citations = CITATION.matcher(answer);
        while (citations.find()) {
            long id = Long.parseLong(citations.group(1));
            if (available.containsKey(id)) {
                valid.add(id);
            } else {
                problems.add(new Problem("UNKNOWN_CITATION",
                        "cited " + Constants.CITATION_PREFIX + id
                                + ", which was not among the retrieved sources"));
            }
        }

        // Quoted spans must appear in one of the retrieved chunks. Whitespace is
        // normalised first: line wrapping differs between the stored chunk and the
        // model's rendering of it, and that difference is not a fabrication.
        String haystack = retrieved.stream()
                .map(c -> TextUtil.normaliseForComparison(c.chunk().getContent()))
                .collect(Collectors.joining("\n"));

        Matcher quotes = QUOTED.matcher(answer);
        while (quotes.find()) {
            String quoted = TextUtil.normaliseForComparison(quotes.group(1));
            if (quoted.length() >= 12 && !haystack.contains(quoted)) {
                problems.add(new Problem("UNSUPPORTED_QUOTE",
                        "quoted text not found in any retrieved source: \""
                                + TextUtil.truncate(quotes.group(1), 80) + "\""));
            }
        }

        boolean clean = problems.isEmpty();
        if (!clean) {
            log.warn("citation verification found {} problems", problems.size());
        }
        return new Result(clean, List.copyOf(valid), List.copyOf(problems));
    }
}
