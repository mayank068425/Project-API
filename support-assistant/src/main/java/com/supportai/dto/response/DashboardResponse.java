package com.supportai.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DashboardResponse {

    private DashboardResponse() {
    }

    /**
     * A duration statistic.
     *
     * @param medianMinutes null when there is no sample. Deliberately not zero:
     *                      zero reads as "we acknowledge instantly" rather than
     *                      "we have no data", and those are very different
     *                      dashboards.
     * @param meanMinutes   reported alongside the median, never instead of it.
     *                      One incident left open over a weekend drags the mean
     *                      far above anything typical.
     */
    public record Stat(Long medianMinutes, Long meanMinutes, Long p90Minutes, int sample) {
    }

    public record Overview(long openIncidents, long sev1Open, long breachedTargets,
                           Stat timeToAcknowledge, Stat timeToMitigate, Stat timeToResolve,
                           Map<String, Long> byService, Map<String, Long> bySeverity,
                           long documentCount, long partialProcedureChunks,
                           AssistantHealth assistant, Instant asOf) {
    }

    /**
     * @param rejectionRate proportion of assistant calls whose output failed
     *                      verification. Worth watching as a trend: a rising rate
     *                      usually means retrieval has degraded, not that the
     *                      model got worse.
     */
    public record AssistantHealth(boolean enabled, long callsLast7Days, long rejectionsLast7Days,
                                  double rejectionRate, List<String> notes) {
    }
}
