package com.supportai.dto.response;

import com.supportai.entity.TimelineEntry;

import java.time.Instant;

/**
 * @param evidence whether this entry may be cited in an analysis. False for
 *                 assistant-authored entries, and surfaced so a reader can see
 *                 which parts of a timeline the RCA was allowed to use.
 */
public record TimelineEntryResponse(Long id, Long incidentId, Instant occurredAt,
                                    Instant recordedAt, TimelineEntry.EntryType entryType,
                                    TimelineEntry.EntrySource source, String content,
                                    String author, boolean evidence) {
}
