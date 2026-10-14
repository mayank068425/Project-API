package com.supportai.service;

import com.supportai.retrieval.RetrievedChunk;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface AuditService {

    String INCIDENT_CREATED = "INCIDENT_CREATED";
    String INCIDENT_UPDATED = "INCIDENT_UPDATED";
    String INCIDENT_TRANSITIONED = "INCIDENT_TRANSITIONED";
    String TIMELINE_ENTRY_ADDED = "TIMELINE_ENTRY_ADDED";
    String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    String DOCUMENT_SUPERSEDED = "DOCUMENT_SUPERSEDED";
    String DOCUMENT_DELETED = "DOCUMENT_DELETED";
    String ASSISTANT_ASK = "ASSISTANT_ASK";
    String ASSISTANT_RCA = "ASSISTANT_RCA";
    String ASSISTANT_DRAFT = "ASSISTANT_DRAFT";
    String LOGIN_FAILED = "LOGIN_FAILED";

    void record(String action, String entityType, String entityId, String detail);

    /**
     * Records an assistant call.
     *
     * @param prompt hashed before storage, never kept. Incident text contains
     *               customer data and a long-retention audit table is the wrong
     *               place for a second copy of it.
     * @param retrieved what the model was shown. Without this you cannot tell,
     *               weeks later, whether a wrong answer came from bad retrieval or
     *               bad generation — and those have different fixes.
     */
    void recordAssistantCall(String action, String entityId, String prompt,
                             List<RetrievedChunk> retrieved, List<String> evidenceRefs,
                             String verificationResult, int correctionCount, long durationMs);

    Page<Map<String, Object>> list(String actor, String action, Pageable pageable);

    /** Rejections as a proportion of assistant calls. A trend worth watching. */
    double rejectionRate(int days);
}
