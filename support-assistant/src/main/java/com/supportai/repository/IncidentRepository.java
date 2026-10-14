package com.supportai.repository;

import com.supportai.entity.Incident;
import com.supportai.entity.IncidentStatus;
import com.supportai.entity.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIncidentNumber(String incidentNumber);

    boolean existsByIncidentNumber(String incidentNumber);

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    Page<Incident> findByService(String service, Pageable pageable);

    Page<Incident> findBySeverity(Severity severity, Pageable pageable);

    @Query("select i from Incident i where i.status not in "
            + "(com.supportai.entity.IncidentStatus.RESOLVED, "
            + "com.supportai.entity.IncidentStatus.CLOSED) "
            + "order by i.severity, i.detectedAt")
    List<Incident> findOpen();

    @Query("select i from Incident i where i.detectedAt between :from and :to")
    List<Incident> findDetectedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Past incidents whose resolution might apply to a new one.
     *
     * <p>Native, because PostgreSQL full-text ranking has no JPQL equivalent.
     * Only incidents with a recorded resolution are returned: one nobody wrote up
     * teaches nothing and costs attention in a suggestion list.
     *
     * <p>The similarity here is lexical only. The caller applies recency decay and
     * a same-service boost, and leads each suggestion with the past incident's
     * <em>root cause</em> — because similar symptoms routinely have different
     * causes, and presenting a fix without its cause is how somebody applies the
     * wrong change confidently.
     */
    @Query(value = "SELECT i.incident_number AS incidentNumber, i.title AS title, "
            + "       i.service AS service, i.severity AS severity, "
            + "       i.root_cause AS rootCause, "
            + "       i.resolution_summary AS resolutionSummary, "
            + "       i.resolved_at AS resolvedAt, "
            + "       ts_rank_cd(to_tsvector('english', i.title || ' ' || i.description), "
            + "                  plainto_tsquery('english', :queryText)) AS similarity "
            + "FROM incidents i "
            + "WHERE i.incident_id <> :excludeId "
            + "  AND i.resolution_summary IS NOT NULL "
            + "  AND i.resolution_summary <> '' "
            + "  AND to_tsvector('english', i.title || ' ' || i.description) "
            + "      @@ plainto_tsquery('english', :queryText) "
            + "ORDER BY similarity DESC "
            + "LIMIT :maxResults", nativeQuery = true)
    List<SimilarIncidentRow> findSimilarResolved(@Param("queryText") String queryText,
                                                 @Param("excludeId") Long excludeId,
                                                 @Param("maxResults") int maxResults);

    /** Projection for the native similarity query. */
    interface SimilarIncidentRow {
        String getIncidentNumber();

        String getTitle();

        String getService();

        String getSeverity();

        String getRootCause();

        String getResolutionSummary();

        Instant getResolvedAt();

        Double getSimilarity();
    }

    long countByStatus(IncidentStatus status);

    long countBySeverity(Severity severity);
}
