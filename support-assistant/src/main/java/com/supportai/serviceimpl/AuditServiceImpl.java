package com.supportai.serviceimpl;

import com.supportai.entity.AuditEvent;
import com.supportai.mapper.AuditMapper;
import com.supportai.repository.AuditEventRepository;
import com.supportai.retrieval.RetrievedChunk;
import com.supportai.security.SecurityUtils;
import com.supportai.service.AuditService;
import com.supportai.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the audit trail.
 *
 * <p>Each write runs in its own transaction and swallows failures. An audit
 * record is a side effect and must never roll back the action it describes:
 * failing an incident update because the audit table was briefly unavailable is
 * strictly worse than a gap in the log.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditEventRepository repository;
    private final AuditMapper mapper;

    public AuditServiceImpl(AuditEventRepository repository, AuditMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, String detail) {
        write(AuditEvent.builder()
                .actor(SecurityUtils.currentUsername())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .detail(TextUtil.truncate(detail, 1000))
                .build());
    }

    /**
     * Records an assistant call.
     *
     * <p>The prompt is hashed, never stored. Incident text contains customer data,
     * and a long-retention audit table is the wrong place for a second copy of it.
     * A SHA-256 still answers the question the audit exists for — was this the
     * same prompt as that one — without creating a retention obligation nobody
     * scoped.
     *
     * <p>What is stored instead is the retrieved chunk ids and the verification
     * outcome. Without those you cannot tell, weeks later, whether a wrong answer
     * came from bad retrieval or bad generation, and those have different fixes.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAssistantCall(String action, String entityId, String prompt,
                                    List<RetrievedChunk> retrieved, List<String> evidenceRefs,
                                    String verificationResult, int correctionCount,
                                    long durationMs) {
        String chunkIds = retrieved == null ? null : retrieved.stream()
                .map(c -> String.valueOf(c.chunk().getId()))
                .collect(Collectors.joining(","));

        write(AuditEvent.builder()
                .actor(SecurityUtils.currentUsername())
                .action(action)
                .entityType("Incident")
                .entityId(entityId)
                .promptHash(sha256(prompt))
                .retrievedChunkIds(TextUtil.truncate(chunkIds, 500))
                .evidenceRefs(evidenceRefs == null ? null
                        : TextUtil.truncate(String.join(",", evidenceRefs), 300))
                .verificationResult(verificationResult)
                .correctionCount(correctionCount)
                .durationMs(durationMs)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(String actor, String action, Pageable pageable) {
        Page<AuditEvent> page;
        if (actor != null && !actor.isBlank()) {
            page = repository.findByActorOrderByOccurredAtDesc(actor, pageable);
        } else if (action != null && !action.isBlank()) {
            page = repository.findByActionOrderByOccurredAtDesc(action, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(mapper::toMap);
    }

    /**
     * Rejections as a proportion of assistant calls.
     *
     * <p>Worth watching as a trend rather than investigating individually. A
     * rising rejection rate usually means retrieval has degraded — a re-index with
     * a different embedding model, or a document set that has drifted away from
     * what people ask — not that the model got worse.
     */
    @Override
    @Transactional(readOnly = true)
    public double rejectionRate(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long calls = repository.countAssistantCallsSince(since);
        if (calls == 0) {
            return 0.0;
        }
        return (double) repository.countRejectionsSince(since) / calls;
    }

    private void write(AuditEvent event) {
        try {
            repository.save(event);
        } catch (RuntimeException ex) {
            log.error("could not write audit entry {} for {} {}", event.getAction(),
                    event.getEntityType(), event.getEntityId(), ex);
        }
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JLS; this cannot happen on a valid JRE.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
