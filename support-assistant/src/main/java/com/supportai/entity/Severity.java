package com.supportai.entity;

import java.time.Duration;

/**
 * Incident severity, with the response targets attached.
 *
 * <p>Targets live here rather than in configuration because they are what
 * severity <em>means</em>. A SEV1 whose acknowledgement target is the same as a
 * SEV3's is not a SEV1.
 */
public enum Severity {

    SEV1("Critical", Duration.ofMinutes(15), Duration.ofHours(4)),
    SEV2("Major", Duration.ofMinutes(30), Duration.ofHours(12)),
    SEV3("Minor", Duration.ofHours(4), Duration.ofDays(3)),
    SEV4("Low", Duration.ofDays(1), Duration.ofDays(14));

    private final String label;
    private final Duration acknowledgeTarget;
    private final Duration resolveTarget;

    Severity(String label, Duration acknowledgeTarget, Duration resolveTarget) {
        this.label = label;
        this.acknowledgeTarget = acknowledgeTarget;
        this.resolveTarget = resolveTarget;
    }

    public String getLabel() {
        return label;
    }

    public Duration getAcknowledgeTarget() {
        return acknowledgeTarget;
    }

    public Duration getResolveTarget() {
        return resolveTarget;
    }
}
