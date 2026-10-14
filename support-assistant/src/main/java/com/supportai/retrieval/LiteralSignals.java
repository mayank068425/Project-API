package com.supportai.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts tokens that must be matched exactly rather than semantically.
 *
 * <h2>The problem</h2>
 * Embeddings are poor at exact identifiers. {@code ORA-01555} embeds into the
 * neighbourhood of "database error text in general" — which is precisely the
 * region the engineer already knows about and is trying to escape. Semantic
 * search returns five documents about database errors and not the one page that
 * names the code.
 *
 * <p>These tokens are what a person actually pasted from a log, and a chunk
 * containing the same string verbatim is almost certainly the right chunk.
 */
public final class LiteralSignals {

    private LiteralSignals() {
    }

    private static final List<Pattern> PATTERNS = List.of(
            // Vendor error codes: ORA-01555, SQLSTATE 23505, MSG-4021
            Pattern.compile("\\b[A-Z]{2,6}-\\d{3,6}\\b"),
            // Fully-qualified exception and error class names
            Pattern.compile("\\b(?:[a-z]\\w*\\.){2,}[A-Z]\\w*(?:Exception|Error)\\b"),
            // Bare exception names, which appear in logs without the package
            Pattern.compile("\\b[A-Z]\\w+(?:Exception|Error|Timeout)\\b"),
            // Pool and component identifiers: HikariPool-1, worker-3
            Pattern.compile("\\b[A-Z][A-Za-z]+(?:Pool|Executor|Scheduler|Client)-\\d+\\b"),
            // Kubernetes resources
            Pattern.compile("\\b(?:deploy|deployment|pod|statefulset|svc|namespace)/[\\w.-]+\\b"),
            // Hostnames with a numeric suffix
            Pattern.compile("\\b(?:[a-z0-9]+-){1,4}\\d{1,3}"
                    + "(?:\\.(?:internal|local|corp|svc))?\\b"),
            // UUIDs
            Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
            // HTTP status codes in context: "503 Service Unavailable"
            Pattern.compile("\\b[45]\\d{2}\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?\\b"));

    /** Distinct literal tokens found in a piece of text, preserving order. */
    public static Set<String> extract(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group().trim());
            }
        }
        return found;
    }

    /** Space-separated form for storage on the chunk row. */
    public static String toStored(Set<String> tokens) {
        return String.join(" ", tokens);
    }

    /**
     * Whether a chunk contains any of the query's literal tokens verbatim.
     *
     * <p>Case-insensitive, because logs and documentation disagree on casing far
     * more often than they disagree on the identifier. Nothing else is
     * normalised: {@code deploy/payments} and {@code deploy/payments-api} must
     * stay distinct.
     */
    public static boolean matches(Set<String> queryTokens, String chunkContent,
                                  String storedTokens) {
        if (queryTokens.isEmpty()) {
            return false;
        }
        String haystack = ((chunkContent == null ? "" : chunkContent) + " "
                + (storedTokens == null ? "" : storedTokens)).toLowerCase(Locale.ENGLISH);

        return queryTokens.stream()
                .map(token -> token.toLowerCase(Locale.ENGLISH))
                .anyMatch(haystack::contains);
    }
}
