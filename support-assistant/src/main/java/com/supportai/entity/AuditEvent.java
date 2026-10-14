package com.supportai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An immutable record of an action, including every assistant call.
 *
 * <h2>The prompt is hashed, not stored</h2>
 * Incident descriptions contain customer data, and a long-retention audit table
 * is the wrong place for a second copy of it. A SHA-256 still answers the
 * question the audit exists for — "was this the same prompt as that one" — while
 * a plaintext copy creates a retention obligation nobody scoped.
 *
 * <h2>What is stored instead</h2>
 * The retrieved chunk ids, the evidence references, and the verification outcome.
 * Without those you cannot tell, weeks later, whether a wrong answer came from
 * bad retrieval or bad generation — and those have completely different fixes.
 */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(name = "ix_audit_at", columnList = "occurred_at"),
        @Index(name = "ix_audit_actor", columnList = "actor"),
        @Index(name = "ix_audit_action", columnList = "action"),
        @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    /** SHA-256 of the prompt. See the class comment. */
    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "model_name", length = 100)
    private String modelName;

    /** Comma-separated chunk ids, so bad retrieval is distinguishable later. */
    @Column(name = "retrieved_chunk_ids", length = 500)
    private String retrievedChunkIds;

    @Column(name = "evidence_refs", length = 300)
    private String evidenceRefs;

    /** CLEAN, CORRECTED or REJECTED. The verification outcome. */
    @Column(name = "verification_result", length = 20)
    private String verificationResult;

    @Column(name = "correction_count")
    private Integer correctionCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 1000)
    private String detail;
}
