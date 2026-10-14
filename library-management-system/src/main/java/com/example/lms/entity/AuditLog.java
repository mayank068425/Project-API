package com.example.lms.entity;

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
 * An immutable record of something that happened.
 *
 * <p>Append only. There is no update path and no delete path, because an audit
 * trail that can be edited answers no question anyone would ask it.
 *
 * <p>Before and after values are stored for catalogue edits. "Book updated"
 * without them tells an investigator that something changed and nothing about
 * what, which is the difference between a log and an audit trail.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "ix_audit_at", columnList = "occurred_at"),
        @Index(name = "ix_audit_actor", columnList = "actor"),
        @Index(name = "ix_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "ix_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    @Column(name = "old_value", length = 2000)
    private String oldValue;

    @Column(name = "new_value", length = 2000)
    private String newValue;

    @Column(length = 500)
    private String detail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
