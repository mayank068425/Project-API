package com.supportai.entity;

import com.supportai.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "ux_incident_number", columnList = "incident_number", unique = true),
        @Index(name = "ix_incident_status", columnList = "status"),
        @Index(name = "ix_incident_service", columnList = "service"),
        @Index(name = "ix_incident_detected", columnList = "detected_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Long id;

    @Column(name = "incident_number", nullable = false, unique = true, length = 20)
    private String incidentNumber;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    /**
     * An assistant-written summary of a long description.
     *
     * <p>Stored beside the original and never replacing it, and deliberately read
     * by nothing else in the system. Summaries are lossy; the detail they drop is
     * occasionally the one that mattered, and a root cause analysis built on a
     * summary is an analysis of an analysis.
     */
    @Column(name = "ai_summary", columnDefinition = "text")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.NEW;

    @Column(nullable = false, length = 100)
    private String service;

    @Column(name = "customer_impacting", nullable = false)
    @Builder.Default
    private boolean customerImpacting = false;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** When customer impact stopped. Distinct from resolution. */
    @Column(name = "mitigated_at")
    private Instant mitigatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * The root cause, written by a person.
     *
     * <p>Nothing in the assistant writes to this field. The RCA feature produces a
     * draft; a human reads it and decides what goes here. That separation is the
     * whole reason a generated analysis is safe to offer at all.
     */
    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(name = "resolution_summary", columnDefinition = "text")
    private String resolutionSummary;

    @Column(name = "reported_by", length = 80)
    private String reportedBy;

    @Column(name = "assigned_to", length = 80)
    private String assignedTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Moves the incident to a new status, stamping the right timestamp.
     *
     * @param at the moment it happened, or null for now. Taking it as a parameter
     *           lets a lead backdate an acknowledgement they made on the phone
     *           before touching the system, which is the common case.
     */
    public void transitionTo(IncidentStatus next, Instant at) {
        if (!status.canTransitionTo(next)) {
            throw new BusinessRuleException("An incident that is " + status
                    + " cannot move to " + next);
        }
        Instant when = at == null ? Instant.now() : at;

        switch (next) {
            case ACKNOWLEDGED -> {
                // Not overwritten on a later transition. The first acknowledgement
                // is the one the response target is measured against.
                if (acknowledgedAt == null) {
                    acknowledgedAt = when;
                }
            }
            case MITIGATED -> mitigatedAt = when;
            case RESOLVED -> {
                resolvedAt = when;
                // Backfilled when an incident went straight to resolved. Without
                // this, time-to-mitigate is silently null for exactly the
                // incidents that were fixed quickly, and the metric quietly
                // excludes its best cases.
                if (mitigatedAt == null) {
                    mitigatedAt = when;
                }
            }
            case INVESTIGATING -> {
                // Reopening. Leaving resolvedAt set would report a resolution time
                // for an incident that is demonstrably not resolved.
                resolvedAt = null;
            }
            default -> { }
        }
        this.status = next;
    }

    public Duration timeToAcknowledge() {
        return acknowledgedAt == null ? null : Duration.between(detectedAt, acknowledgedAt);
    }

    public Duration timeToMitigate() {
        return mitigatedAt == null ? null : Duration.between(detectedAt, mitigatedAt);
    }

    public Duration timeToResolve() {
        return resolvedAt == null ? null : Duration.between(detectedAt, resolvedAt);
    }

    /**
     * Whether the acknowledgement target was missed.
     *
     * <p>An unacknowledged incident is measured against now, not left null. A
     * SEV1 nobody has touched for two hours has breached its target, and a
     * dashboard that only counts acknowledged incidents would show it as fine.
     */
    public boolean breachedAcknowledgeTarget() {
        Duration elapsed = acknowledgedAt == null
                ? Duration.between(detectedAt, Instant.now())
                : timeToAcknowledge();
        return elapsed.compareTo(severity.getAcknowledgeTarget()) > 0;
    }

    public boolean breachedResolveTarget() {
        Duration elapsed = resolvedAt == null
                ? Duration.between(detectedAt, Instant.now())
                : timeToResolve();
        return elapsed.compareTo(severity.getResolveTarget()) > 0;
    }

    /**
     * Whether this incident is usable as precedent for another.
     *
     * <p>Requires a written resolution, not merely a RESOLVED status. An incident
     * somebody closed without recording what they did teaches nothing and costs
     * attention when it appears in a suggestion list.
     */
    public boolean hasUsableResolution() {
        return resolutionSummary != null && !resolutionSummary.isBlank();
    }
}
