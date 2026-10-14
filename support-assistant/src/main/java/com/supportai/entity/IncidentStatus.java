package com.supportai.entity;

import java.util.Set;

/**
 * Incident lifecycle.
 *
 * <p>{@code MITIGATED} is deliberately separate from {@code RESOLVED}. Customer
 * impact usually stops well before the fault is actually fixed — a failover stops
 * the bleeding, the real repair lands days later. Collapsing them makes
 * time-to-mitigate unmeasurable and overstates impact on every incident.
 */
public enum IncidentStatus {

    NEW,
    ACKNOWLEDGED,
    INVESTIGATING,
    /** Customer impact has stopped. The underlying fault may remain. */
    MITIGATED,
    RESOLVED,
    CLOSED;

    public boolean isOpen() {
        return this != RESOLVED && this != CLOSED;
    }

    /**
     * Legal next states.
     *
     * <p>Reopening from RESOLVED is permitted — incidents recur, and forcing a
     * new record loses the connection to the original investigation. Reopening
     * from CLOSED is not: closure is the point after which the record is
     * referenced by postmortems and reports.
     */
    public Set<IncidentStatus> allowedNext() {
        return switch (this) {
            case NEW -> Set.of(ACKNOWLEDGED, INVESTIGATING, MITIGATED, RESOLVED);
            case ACKNOWLEDGED -> Set.of(INVESTIGATING, MITIGATED, RESOLVED);
            case INVESTIGATING -> Set.of(MITIGATED, RESOLVED);
            case MITIGATED -> Set.of(INVESTIGATING, RESOLVED);
            case RESOLVED -> Set.of(INVESTIGATING, CLOSED);
            case CLOSED -> Set.of();
        };
    }

    public boolean canTransitionTo(IncidentStatus next) {
        return allowedNext().contains(next);
    }
}
