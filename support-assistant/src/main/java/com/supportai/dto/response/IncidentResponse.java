package com.supportai.dto.response;

import com.supportai.entity.IncidentStatus;
import com.supportai.entity.Severity;

import java.time.Instant;

/**
 * @param timeToMitigateMinutes distinct from resolution. Customer impact usually
 *                              stops well before the fault is fixed.
 * @param breachedAcknowledgeTarget computed against now for an unacknowledged
 *                              incident, so a SEV1 nobody has touched shows as
 *                              breached rather than as null
 */
public record IncidentResponse(Long id, String incidentNumber, String title, String description,
                               String aiSummary, Severity severity, String severityLabel,
                               IncidentStatus status, String service, boolean customerImpacting,
                               Instant detectedAt, Instant acknowledgedAt, Instant mitigatedAt,
                               Instant resolvedAt, String rootCause, String resolutionSummary,
                               String reportedBy, String assignedTo,
                               Long timeToAcknowledgeMinutes, Long timeToMitigateMinutes,
                               Long timeToResolveMinutes, boolean breachedAcknowledgeTarget,
                               boolean breachedResolveTarget, long timelineEntryCount) {
}
