package com.supportai.repository;

import com.supportai.entity.TimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, Long> {

    /**
     * A timeline in the order things happened.
     *
     * <p>Ordered by {@code occurredAt}, not by id or insertion time. Engineers
     * write up the last twenty minutes once the fire is out, so insertion order is
     * a sequence of events that never happened — and the RCA would reason from it.
     */
    List<TimelineEntry> findByIncidentIdOrderByOccurredAtAsc(Long incidentId);

    /**
     * Only entries that may be cited as evidence.
     *
     * <p>Excludes assistant-authored entries. An analysis treating its own prior
     * output as an observation compounds its errors until nothing traces back to
     * what actually happened.
     */
    @Query("select t from TimelineEntry t where t.incidentId = :incidentId "
            + "and t.source <> com.supportai.entity.TimelineEntry.EntrySource.ASSISTANT "
            + "order by t.occurredAt asc")
    List<TimelineEntry> findEvidenceFor(@Param("incidentId") Long incidentId);

    @Query("select count(t) from TimelineEntry t where t.incidentId = :incidentId "
            + "and t.source <> com.supportai.entity.TimelineEntry.EntrySource.ASSISTANT")
    long countEvidenceFor(@Param("incidentId") Long incidentId);

    long countByIncidentId(Long incidentId);
}
