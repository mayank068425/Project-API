package com.example.lms.serviceimpl;

import com.example.lms.entity.AuditLog;
import com.example.lms.repository.AuditLogRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>Each write runs in its own transaction and swallows failures. An audit
 * record is a side effect of an action and must never roll that action back:
 * failing to return a book because the audit table was briefly unavailable is
 * strictly worse than a gap in the log.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository repository;

    public AuditServiceImpl(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, String detail) {
        write(builder(action, entityType, entityId).detail(truncate(detail, 500)).build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(String action, String entityType, String entityId,
                             String oldValue, String newValue) {
        write(builder(action, entityType, entityId)
                .oldValue(truncate(oldValue, 2000))
                .newValue(truncate(newValue, 2000))
                .build());
    }

    private AuditLog.AuditLogBuilder builder(String action, String entityType, String entityId) {
        String role;
        try {
            role = String.join(",", SecurityUtils.currentUser().getRoleNames());
        } catch (RuntimeException ex) {
            // Scheduled sweeps run with no authenticated principal.
            role = "SYSTEM";
        }
        return AuditLog.builder()
                .actor(SecurityUtils.currentUsername())
                .actorRole(role)
                .action(action)
                .entityType(entityType)
                .entityId(entityId);
    }

    private void write(AuditLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException ex) {
            log.error("could not write audit entry {} for {} {}", entry.getAction(),
                    entry.getEntityType(), entry.getEntityId(), ex);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
