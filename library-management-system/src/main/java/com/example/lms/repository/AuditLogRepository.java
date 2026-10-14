package com.example.lms.repository;

import com.example.lms.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorOrderByOccurredAtDesc(String actor, Pageable pageable);

    Page<AuditLog> findByActionOrderByOccurredAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(
            String entityType, String entityId, Pageable pageable);

    @Query("select a from AuditLog a where a.occurredAt between :from and :to "
            + "order by a.occurredAt desc")
    Page<AuditLog> findBetween(@Param("from") Instant from, @Param("to") Instant to,
                               Pageable pageable);
}
