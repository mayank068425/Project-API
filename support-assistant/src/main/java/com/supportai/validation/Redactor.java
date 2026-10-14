package com.supportai.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips internal detail from text destined for customers.
 *
 * <p>Customer updates are drafted from the incident record, and the incident
 * record is full of things customers must not see: internal hostnames, private
 * addresses, stack traces, ticket references, colleagues' names. A model asked to
 * write a customer email from that material will usually keep some of it, because
 * it reads as helpful specificity.
 *
 * <p>This runs on the draft <em>after</em> generation. The instruction in the
 * prompt is not the control; this is. Prompts are advisory and a regular
 * expression is not.
 */
public final class Redactor {

    public record Redaction(String kind, String original, String replacement) {
    }

    public record Result(String text, List<Redaction> redactions) {

        public boolean isClean() {
            return redactions.isEmpty();
        }
    }

    private record Rule(String kind, Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("stack-frame",
                    Pattern.compile("\\bat\\s+[\\w.$]+\\([\\w.]+:\\d+\\)"),
                    "[internal diagnostic detail removed]"),
            new Rule("exception-class",
                    Pattern.compile("\\b(?:[a-z]\\w*\\.){2,}[A-Z]\\w*(?:Exception|Error)\\b"),
                    "[internal error type removed]"),
            new Rule("ipv4",
                    Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b"),
                    "[internal address removed]"),
            new Rule("internal-hostname",
                    Pattern.compile("\\b(?:[a-z0-9]+-){1,4}\\d{1,3}"
                            + "(?:\\.(?:internal|local|corp|svc|cluster\\.local))?\\b"),
                    "[internal host removed]"),
            new Rule("kubernetes-resource",
                    Pattern.compile("\\b(?:deploy|deployment|pod|statefulset|namespace)/"
                            + "[\\w.-]+\\b"),
                    "[internal resource removed]"),
            new Rule("internal-ticket",
                    Pattern.compile("\\b(?:INC|CHG|PRB|JIRA|OPS|SRE)-\\d{3,}\\b"),
                    "[internal reference removed]"),
            new Rule("internal-url",
                    Pattern.compile("https?://[\\w.-]*(?:internal|intranet|corp|local|"
                            + "grafana|kibana|splunk|jenkins)[\\w./?=&#-]*"),
                    "[internal link removed]"),
            new Rule("email",
                    Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.]{2,}\\b"),
                    "[contact detail removed]"),
            new Rule("connection-string",
                    Pattern.compile("\\b(?:jdbc|mongodb|redis|amqp|postgres(?:ql)?)://[^\\s]+"),
                    "[internal connection detail removed]"));

    /**
     * Phrases promising something the incident record cannot support.
     *
     * <p>Flagged rather than rewritten. A commitment to a restoration time in a
     * customer email is a contractual statement, and the correct handling is to
     * put it in front of the person accountable for it — not to quietly delete it
     * and leave them unaware it was ever drafted.
     */
    private static final List<Pattern> COMMITMENTS = List.of(
            Pattern.compile("(?i)\\b(?:will be|shall be)\\s+(?:fully\\s+)?"
                    + "(?:resolved|restored|fixed)\\s+(?:by|within|in)\\b"),
            Pattern.compile("(?i)\\bguarantee(?:d|s)?\\b"),
            Pattern.compile("(?i)\\bno\\s+data\\s+(?:was\\s+)?"
                    + "(?:lost|affected|compromised)\\b"),
            Pattern.compile("(?i)\\bcompensat(?:e|ion|ed)\\b"),
            Pattern.compile("(?i)\\bSLA\\s+credit\\b"));

    private Redactor() {
    }

    public static Result redact(String text) {
        if (text == null || text.isBlank()) {
            return new Result(text, List.of());
        }
        String working = text;
        List<Redaction> redactions = new ArrayList<>();

        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(working);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                redactions.add(new Redaction(rule.kind(), matcher.group(), rule.replacement()));
                matcher.appendReplacement(out, Matcher.quoteReplacement(rule.replacement()));
            }
            matcher.appendTail(out);
            working = out.toString();
        }
        return new Result(working, List.copyOf(redactions));
    }

    /** Commitment-like phrasing a human must approve before this is sent. */
    public static List<String> findCommitments(String text) {
        if (text == null) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        for (Pattern pattern : COMMITMENTS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                found.add(matcher.group());
            }
        }
        return List.copyOf(found);
    }
}
