package com.supportai.validation;

import com.supportai.dto.request.IncidentRequest;
import com.supportai.entity.Incident;
import com.supportai.exception.BusinessRuleException;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * Incident rules a Bean Validation annotation cannot express.
 */
@Component
public class IncidentValidator {

    /** Beyond this, a detection time is a typo rather than a backdated report. */
    private static final Duration MAX_BACKDATE = Duration.ofDays(90);

    public void validateForCreate(IncidentRequest request) {
        requirePresent(request.title(), "Title");
        requirePresent(request.description(), "Description");
        requirePresent(request.service(), "Service");

        if (request.severity() == null) {
            throw new BusinessRuleException("A severity is required");
        }
        validateDetectedAt(request.detectedAt());
    }

    public void validateForUpdate(IncidentRequest request, Incident existing) {
        if (request.detectedAt() != null) {
            validateDetectedAt(request.detectedAt());
        }
        if (existing.getStatus() == com.supportai.entity.IncidentStatus.CLOSED) {
            // A closed incident is referenced by postmortems and reports. Editing
            // it changes the account of what happened after people have relied on
            // it.
            throw new BusinessRuleException(
                    "Incident " + existing.getIncidentNumber() + " is closed and cannot be edited");
        }
    }

    /**
     * Requires enough evidence before an RCA is attempted.
     *
     * <p>Refusing is the correct answer, not a limitation. Asked to explain an
     * incident with two lines on its timeline, a model produces a confident and
     * entirely invented cause — and it is indistinguishable from a real one,
     * because it is written in the same register with the same structure.
     */
    public void validateEvidenceSufficient(Incident incident, long evidenceCount, int minimum) {
        if (evidenceCount < minimum) {
            throw new com.supportai.exception.AssistantException(
                    com.supportai.exception.AssistantException.Kind.INSUFFICIENT_CONTEXT,
                    "Incident " + incident.getIncidentNumber() + " has " + evidenceCount
                            + " timeline entries that count as evidence. At least " + minimum
                            + " are needed before a root cause analysis can be attempted. "
                            + "Add what was observed and what was tried, then try again.");
        }
    }

    private void validateDetectedAt(Instant detectedAt) {
        if (detectedAt == null) {
            throw new BusinessRuleException("A detection time is required");
        }
        if (detectedAt.isAfter(Instant.now().plus(Duration.ofMinutes(5)))) {
            // Five minutes of tolerance for clock skew between the reporting host
            // and the database, which is real and not worth failing on.
            throw new BusinessRuleException("The detection time is in the future");
        }
        if (detectedAt.isBefore(Instant.now().minus(MAX_BACKDATE))) {
            throw new BusinessRuleException("The detection time is more than "
                    + MAX_BACKDATE.toDays() + " days ago; check the value");
        }
    }

    private void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
    }
}
