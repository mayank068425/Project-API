package com.supportai.serviceimpl;

import com.supportai.config.AssistantProperties;
import com.supportai.dto.response.DashboardResponse;
import com.supportai.entity.Incident;
import com.supportai.entity.IncidentStatus;
import com.supportai.entity.Severity;
import com.supportai.repository.DocumentChunkRepository;
import com.supportai.repository.IncidentRepository;
import com.supportai.repository.KbDocumentRepository;
import com.supportai.service.AuditService;
import com.supportai.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final IncidentRepository incidents;
    private final KbDocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final AuditService audit;
    private final AssistantProperties properties;

    public DashboardServiceImpl(IncidentRepository incidents, KbDocumentRepository documents,
                                DocumentChunkRepository chunks, AuditService audit,
                                AssistantProperties properties) {
        this.incidents = incidents;
        this.documents = documents;
        this.chunks = chunks;
        this.audit = audit;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse.Overview overview() {
        List<Incident> recent = incidents.findDetectedBetween(
                Instant.now().minus(90, ChronoUnit.DAYS), Instant.now());

        Map<String, Long> byService = recent.stream().collect(Collectors.groupingBy(
                Incident::getService, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> bySeverity = recent.stream().collect(Collectors.groupingBy(
                i -> i.getSeverity().name(), LinkedHashMap::new, Collectors.counting()));

        double rejectionRate = audit.rejectionRate(7);

        List<String> notes = new ArrayList<>();
        if (!properties.isEnabled()) {
            notes.add("The assistant is disabled. Incidents, search, this dashboard and "
                    + "audit are unaffected.");
        }
        if (rejectionRate > 0.2) {
            // A rising rejection rate usually means retrieval has degraded, not
            // that the model got worse. Saying so points the investigation at the
            // right place.
            notes.add(String.format("%.0f%% of assistant outputs failed verification in the "
                    + "last 7 days. This usually indicates degraded retrieval rather than a "
                    + "model problem: check whether documents were re-indexed with a "
                    + "different embedding model.", rejectionRate * 100));
        }
        long partial = chunks.countPartialProcedures();
        if (partial > 0) {
            notes.add(partial + " chunk(s) contain numbered steps that may not be the full "
                    + "procedure. Answers citing them carry a warning.");
        }

        return new DashboardResponse.Overview(
                incidents.findOpen().size(),
                incidents.findOpen().stream()
                        .filter(i -> i.getSeverity() == Severity.SEV1).count(),
                incidents.findOpen().stream()
                        .filter(Incident::breachedAcknowledgeTarget).count(),
                stat(recent.stream().map(Incident::timeToAcknowledge).toList()),
                stat(recent.stream().map(Incident::timeToMitigate).toList()),
                stat(recent.stream().map(Incident::timeToResolve).toList()),
                byService, bySeverity,
                documents.countByCurrentVersionTrue(),
                partial,
                new DashboardResponse.AssistantHealth(properties.isEnabled(),
                        0, 0, rejectionRate, List.copyOf(notes)),
                Instant.now());
    }

    /**
     * Median, mean and p90 for a set of durations.
     *
     * <p>All three, because the mean alone misleads. One incident left open over a
     * weekend drags it far above anything typical, and a team reading only the
     * mean concludes their response times are getting worse when the distribution
     * has not moved.
     *
     * <p>Nulls are excluded rather than counted as zero. An incident that was never
     * acknowledged has no acknowledgement time; treating that as instant would
     * improve the figure by leaving out the worst cases.
     */
    public static DashboardResponse.Stat stat(List<Duration> durations) {
        List<Long> minutes = durations.stream()
                .filter(java.util.Objects::nonNull)
                .map(Duration::toMinutes)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (minutes.isEmpty()) {
            // Null, not zero. Zero reads as "we acknowledge instantly" rather
            // than "we have no data", and those are very different dashboards.
            return new DashboardResponse.Stat(null, null, null, 0);
        }

        long sum = minutes.stream().mapToLong(Long::longValue).sum();
        return new DashboardResponse.Stat(
                percentile(minutes, 0.5),
                sum / minutes.size(),
                percentile(minutes, 0.9),
                minutes.size());
    }

    /** Nearest rank. Simple, and it never invents a value that is not in the data. */
    public static Long percentile(List<Long> sorted, double fraction) {
        if (sorted.isEmpty()) {
            return null;
        }
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
