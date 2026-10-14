package com.supportai.service;

import com.supportai.entity.Incident;
import com.supportai.entity.IncidentStatus;
import com.supportai.entity.Severity;
import com.supportai.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentLifecycleTest {

    private Incident incident() {
        return Incident.builder()
                .incidentNumber("INC-000001")
                .title("Payments latency")
                .description("p99 above 5s")
                .severity(Severity.SEV1)
                .status(IncidentStatus.NEW)
                .service("payments")
                .detectedAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();
    }

    @Test
    @DisplayName("resolving without a mitigation backfills the mitigation time")
    void resolvingBackfillsMitigation() {
        // Otherwise time-to-mitigate is silently null for every incident fixed
        // quickly enough to skip the step, and the metric quietly excludes its
        // own best cases.
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, null);
        incident.transitionTo(IncidentStatus.RESOLVED, null);

        assertThat(incident.getMitigatedAt()).isEqualTo(incident.getResolvedAt());
        assertThat(incident.timeToMitigate()).isNotNull();
    }

    @Test
    @DisplayName("reopening clears the resolution time")
    void reopeningClearsResolution() {
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, null);
        incident.transitionTo(IncidentStatus.RESOLVED, null);
        assertThat(incident.getResolvedAt()).isNotNull();

        incident.transitionTo(IncidentStatus.INVESTIGATING, null);

        // Leaving it set would report a resolution time for an incident that is
        // demonstrably not resolved.
        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("acknowledging twice keeps the first acknowledgement time")
    void acknowledgementIsNotOverwritten() {
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, null);
        Instant first = incident.getAcknowledgedAt();

        incident.transitionTo(IncidentStatus.INVESTIGATING, null);

        assertThat(incident.getAcknowledgedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("a closed incident cannot be reopened")
    void closedIsTerminal() {
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, null);
        incident.transitionTo(IncidentStatus.RESOLVED, null);
        incident.transitionTo(IncidentStatus.CLOSED, null);

        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.INVESTIGATING, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("mitigation and resolution are measured separately")
    void mitigationIsDistinctFromResolution() {
        // Customer impact stopped at 40 minutes; the fix landed at 150.
        // Collapsing the two would overstate impact by nearly two hours.
        Instant detected = Instant.now().minus(3, ChronoUnit.HOURS);
        Incident incident = incident();
        incident.setDetectedAt(detected);

        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, detected.plus(5, ChronoUnit.MINUTES));
        incident.transitionTo(IncidentStatus.MITIGATED, detected.plus(40, ChronoUnit.MINUTES));
        incident.transitionTo(IncidentStatus.RESOLVED, detected.plus(150, ChronoUnit.MINUTES));

        assertThat(incident.timeToMitigate().toMinutes()).isEqualTo(40);
        assertThat(incident.timeToResolve().toMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("an unacknowledged incident is measured against now")
    void unacknowledgedBreachIsDetected() {
        // A SEV1 nobody has touched for two hours has breached its 15-minute
        // target. A dashboard counting only acknowledged incidents shows it fine.
        Incident incident = incident();

        assertThat(incident.getAcknowledgedAt()).isNull();
        assertThat(incident.breachedAcknowledgeTarget()).isTrue();
    }

    @Test
    @DisplayName("an incident is only precedent once it has a written resolution")
    void usableResolutionRequiresText() {
        Incident incident = incident();
        incident.transitionTo(IncidentStatus.ACKNOWLEDGED, null);
        incident.transitionTo(IncidentStatus.RESOLVED, null);

        // RESOLVED status alone is not enough. An incident somebody closed without
        // recording what they did teaches nothing and costs attention.
        assertThat(incident.hasUsableResolution()).isFalse();

        incident.setResolutionSummary("Set a read timeout on the downstream client");
        assertThat(incident.hasUsableResolution()).isTrue();
    }
}
