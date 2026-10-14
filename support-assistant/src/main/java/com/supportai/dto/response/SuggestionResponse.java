package com.supportai.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * A past incident that might be precedent.
 *
 * <p>{@code rootCause} comes first in the record on purpose. Text similarity
 * retrieves incidents with similar <em>symptoms</em>, and symptoms are a poor
 * guide to cause: "connection pool exhausted" has perhaps fifteen distinct
 * causes. Presenting the fix without the cause leads an engineer to apply a
 * change that is at best irrelevant and at worst harmful, while looking
 * authoritative.
 *
 * @param cautions reasons this precedent might not apply. Shown, not filtered
 *                 out, because the caller is better placed to judge than a
 *                 threshold is.
 */
public record SuggestionResponse(String incidentNumber, String title, String service,
                                 String rootCause, String resolutionSummary,
                                 Instant resolvedAt, double score, double lexicalScore,
                                 double recencyFactor, List<String> reasons,
                                 List<String> cautions) {
}
