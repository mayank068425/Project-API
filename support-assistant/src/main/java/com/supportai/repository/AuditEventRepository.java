package com.supportai.repository;

import com.supportai.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByActorOrderByOccurredAtDesc(String actor, Pageable pageable);

    Page<AuditEvent> findByActionOrderByOccurredAtDesc(String action, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, String entityId, Pageable pageable);

    @Query("select a from AuditEvent a where a.occurredAt between :from and :to "
            + "order by a.occurredAt desc")
    Page<AuditEvent> findBetween(@Param("from") Instant from, @Param("to") Instant to,
                                 Pageable pageable);

    /**
     * Assistant calls whose output was rejected by verification.
     *
     * <p>Worth watching as a rate rather than individually. A rising rejection
     * rate usually means retrieval has degraded — a re-index with a different
     * embedding model, or a document set that has drifted from what people ask.
     */
    @Query("select count(a) from AuditEvent a where a.verificationResult = 'REJECTED' "
            + "and a.occurredAt >= :since")
    long countRejectionsSince(@Param("since") Instant since);

    @Query("select count(a) from AuditEvent a where a.promptHash is not null "
            + "and a.occurredAt >= :since")
    long countAssistantCallsSince(@Param("since") Instant since);
}
