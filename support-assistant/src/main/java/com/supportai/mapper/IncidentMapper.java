package com.supportai.mapper;

import com.supportai.dto.response.IncidentResponse;
import com.supportai.dto.response.TimelineEntryResponse;
import com.supportai.entity.Incident;
import com.supportai.entity.TimelineEntry;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Incident and timeline entity to response.
 *
 * <p>Hand-written. The derived figures here — the three durations and the two
 * breach flags — are computed on the entity rather than stored, and a reflective
 * mapper would copy the raw timestamps and quietly drop all of them.
 */
@Component
public class IncidentMapper {

    /**
     * @param timelineEntryCount supplied by the caller from a count query, not
     *                           from a collection on the entity. A thousand-entry
     *                           timeline loaded to call {@code size()} is the N+1
     *                           this DTO layer exists to prevent.
     */
    public IncidentResponse toResponse(Incident incident, long timelineEntryCount) {
        if (incident == null) {
            return null;
        }
        return new IncidentResponse(
                incident.getId(), incident.getIncidentNumber(), incident.getTitle(),
                incident.getDescription(), incident.getAiSummary(),
                incident.getSeverity(), incident.getSeverity().getLabel(),
                incident.getStatus(), incident.getService(), incident.isCustomerImpacting(),
                incident.getDetectedAt(), incident.getAcknowledgedAt(),
                incident.getMitigatedAt(), incident.getResolvedAt(),
                incident.getRootCause(), incident.getResolutionSummary(),
                incident.getReportedBy(), incident.getAssignedTo(),
                minutes(incident.timeToAcknowledge()),
                minutes(incident.timeToMitigate()),
                minutes(incident.timeToResolve()),
                incident.breachedAcknowledgeTarget(),
                incident.breachedResolveTarget(),
                timelineEntryCount);
    }

    public IncidentResponse toResponse(Incident incident) {
        return toResponse(incident, 0);
    }

    public TimelineEntryResponse toResponse(TimelineEntry entry) {
        if (entry == null) {
            return null;
        }
        return new TimelineEntryResponse(entry.getId(), entry.getIncidentId(),
                entry.getOccurredAt(), entry.getRecordedAt(), entry.getEntryType(),
                entry.getSource(), entry.getContent(), entry.getAuthor(),
                // Surfaced so a reader can see which entries the RCA was allowed
                // to use. Assistant-authored entries are not evidence.
                entry.isEvidence());
    }

    /** Null stays null. A missing timestamp is not a duration of zero. */
    private Long minutes(Duration duration) {
        return duration == null ? null : duration.toMinutes();
    }
}
