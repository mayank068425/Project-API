package com.supportai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One thing that happened during an incident.
 *
 * <h2>Why the source is recorded</h2>
 * {@link EntrySource#ASSISTANT} entries are excluded from RCA evidence. An
 * analysis that treats its own earlier output as an observation compounds its own
 * errors, and after a couple of rounds the conclusion traces back to nothing that
 * actually happened — while still citing references that look real.
 *
 * <p>Append only. Correcting the record means adding an entry, not editing one:
 * the timeline is the evidence every generated analysis is grounded against, and
 * evidence that can be rewritten after the fact is not evidence.
 */
@Entity
@Table(name = "timeline_entries", indexes = {
        @Index(name = "ix_timeline_incident", columnList = "incident_id, occurred_at"),
        @Index(name = "ix_timeline_source", columnList = "source")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineEntry {

    public enum EntryType {
        OBSERVATION,
        ACTION,
        /** A decision and its reasoning. */
        DECISION,
        STATUS_CHANGE,
        /** Assistant output recorded for traceability. Never evidence. */
        ANALYSIS
    }

    public enum EntrySource {
        HUMAN,
        /** An alert or automated check. Evidence: something really fired. */
        SYSTEM,
        /** Generated. Excluded from evidence. */
        ASSISTANT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entry_id")
    private Long id;

    /**
     * The incident, by id rather than as an association.
     *
     * <p>Timelines are read as a block and written one row at a time; nothing ever
     * navigates from an entry back to the incident. A {@code @ManyToOne} here
     * would add a lazy proxy to every row of a thousand-entry timeline for no
     * benefit.
     */
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    /**
     * When the thing happened, which is not when it was recorded.
     *
     * <p>Engineers write up the last twenty minutes once the fire is out. Ordering
     * a timeline by insertion time produces a sequence of events that never
     * happened in that order, and the RCA then reasons from it.
     */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private EntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntrySource source = EntrySource.HUMAN;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "author", length = 80)
    private String author;

    /**
     * Whether this entry may be cited as evidence in an analysis.
     *
     * <p>Anything the assistant wrote is not. See the class comment.
     */
    public boolean isEvidence() {
        return source != EntrySource.ASSISTANT;
    }
}
