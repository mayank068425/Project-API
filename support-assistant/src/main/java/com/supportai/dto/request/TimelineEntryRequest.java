package com.supportai.dto.request;

import com.supportai.entity.TimelineEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Add something to an incident timeline.
 *
 * @param occurredAt when it happened, which is not when it is being written.
 *                   Engineers write up the last twenty minutes once the fire is
 *                   out; without this the timeline records a sequence of events
 *                   that never occurred in that order, and the RCA reasons from
 *                   it.
 */
public record TimelineEntryRequest(
        @NotNull(message = "An entry type is required") TimelineEntry.EntryType entryType,
        @NotBlank(message = "Content is required") String content,
        Instant occurredAt) {
}
