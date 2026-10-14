package com.supportai.validation;

import com.supportai.entity.TimelineEntry;
import com.supportai.util.Constants;
import com.supportai.util.TextUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The evidence an analysis may draw on, keyed by a short reference to cite.
 *
 * <p>Assistant-generated timeline entries are excluded. An analysis that treats
 * its own previous output as an observation compounds its own errors, and after a
 * couple of rounds the conclusion traces back to nothing that happened — while
 * still citing references that resolve.
 */
public final class EvidenceIndex {

    private final Map<String, TimelineEntry> byReference = new LinkedHashMap<>();

    public static EvidenceIndex of(List<TimelineEntry> entries) {
        EvidenceIndex index = new EvidenceIndex();
        int n = 1;
        for (TimelineEntry entry : entries) {
            if (entry.isEvidence()) {
                index.byReference.put(Constants.EVIDENCE_PREFIX + n++, entry);
            }
        }
        return index;
    }

    public boolean contains(String reference) {
        return reference != null && byReference.containsKey(normalise(reference));
    }

    public TimelineEntry get(String reference) {
        return reference == null ? null : byReference.get(normalise(reference));
    }

    public Set<String> references() {
        return byReference.keySet();
    }

    public int size() {
        return byReference.size();
    }

    public boolean isEmpty() {
        return byReference.isEmpty();
    }

    /** Renders the evidence for the prompt, one reference per line. */
    public String forPrompt() {
        StringBuilder sb = new StringBuilder();
        byReference.forEach((reference, entry) -> sb
                .append('[').append(reference).append("] ")
                .append(entry.getOccurredAt()).append(' ')
                .append('(').append(entry.getEntryType()).append(", ")
                .append(entry.getSource()).append(") ")
                .append(TextUtil.flatten(entry.getContent()))
                .append('\n'));
        return sb.toString();
    }

    private static String normalise(String reference) {
        return reference.trim().toUpperCase(java.util.Locale.ENGLISH);
    }
}
