package com.supportai.dto.request;

import com.supportai.entity.Severity;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Create or update an incident.
 *
 * @param detectedAt when it started, not when it was reported. Taken from the
 *                   caller because the alert fired before anyone opened this
 *                   form, and every response metric measures from here.
 */
public record IncidentRequest(
        @Size(max = 300) String title,
        String description,
        Severity severity,
        @Size(max = 100) String service,
        Boolean customerImpacting,
        Instant detectedAt,
        @Size(max = 80) String assignedTo) {
}
