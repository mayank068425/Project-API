package com.supportai.service;

import com.supportai.dto.request.DocumentUploadRequest;
import com.supportai.dto.response.ChunkResponse;
import com.supportai.dto.response.DocumentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface KnowledgeService {

    /**
     * Ingests a document, chunking and embedding it.
     *
     * <p>Uploading against an existing slug creates a new version and
     * <em>deletes</em> the previous version's chunks and vectors. Leaving them is
     * the quiet failure of most RAG systems: the old chunks keep matching, keep
     * scoring well, and keep carrying a citation that resolves to a real document.
     */
    DocumentResponse upload(DocumentUploadRequest request);

    DocumentResponse get(Long id);

    Page<DocumentResponse> list(Pageable pageable);

    List<DocumentResponse> versionsOf(String slug);

    /** Removes a document and its chunks. Admin only. */
    void delete(Long id);

    /**
     * Hybrid search. Works with the assistant disabled — this is the fallback
     * that makes the assistant optional rather than load-bearing.
     */
    List<ChunkResponse> search(String query, String service, int topK);
}
