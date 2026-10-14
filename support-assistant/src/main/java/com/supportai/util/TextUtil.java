package com.supportai.util;

import java.util.Locale;

public final class TextUtil {

    private TextUtil() {
    }

    /**
     * Collapses whitespace and lowercases, and does nothing else.
     *
     * <p>Deliberately not more aggressive. Stripping punctuation would make
     * {@code --dry-run} and {@code dry run} compare equal, and in a runbook those
     * are different commands with different consequences. Used by the citation
     * check, where over-normalising would let an altered command pass.
     */
    public static String normaliseForComparison(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ENGLISH);
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    public static String flatten(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
