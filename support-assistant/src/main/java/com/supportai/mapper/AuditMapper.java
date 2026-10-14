package com.supportai.mapper;

import com.supportai.entity.AuditEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audit entity to a plain map.
 *
 * <p>A map rather than a record because audit rows are read by humans and by
 * export tooling, and the useful fields differ between an assistant call and a
 * status change. Nulls are dropped so an incident edit does not render six empty
 * AI columns.
 *
 * <p>Note what is never mapped: there is no prompt field to map. Only its hash is
 * stored.
 */
@Component
public class AuditMapper {

    public Map<String, Object> toMap(AuditEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "id", event.getId());
        put(map, "occurredAt", event.getOccurredAt());
        put(map, "actor", event.getActor());
        put(map, "action", event.getAction());
        put(map, "entityType", event.getEntityType());
        put(map, "entityId", event.getEntityId());
        put(map, "promptHash", event.getPromptHash());
        put(map, "modelName", event.getModelName());
        put(map, "retrievedChunkIds", event.getRetrievedChunkIds());
        put(map, "evidenceRefs", event.getEvidenceRefs());
        put(map, "verificationResult", event.getVerificationResult());
        put(map, "correctionCount", event.getCorrectionCount());
        put(map, "durationMs", event.getDurationMs());
        put(map, "detail", event.getDetail());
        return map;
    }

    private void put(Map<String, Object> map, String key, Object value) {
        if (value != null && !(value instanceof Instant instant && instant.equals(Instant.EPOCH))) {
            map.put(key, value);
        }
    }
}
