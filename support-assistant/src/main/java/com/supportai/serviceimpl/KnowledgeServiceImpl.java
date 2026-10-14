package com.supportai.serviceimpl;

import com.supportai.config.AssistantConfig;
import com.supportai.config.AssistantProperties;
import com.supportai.dto.request.DocumentUploadRequest;
import com.supportai.dto.response.ChunkResponse;
import com.supportai.dto.response.DocumentResponse;
import com.supportai.entity.Classification;
import com.supportai.entity.DocumentChunk;
import com.supportai.entity.KbDocument;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.mapper.DocumentMapper;
import com.supportai.repository.DocumentChunkRepository;
import com.supportai.repository.KbDocumentRepository;
import com.supportai.retrieval.HybridRetriever;
import com.supportai.retrieval.LiteralSignals;
import com.supportai.retrieval.RetrievedChunk;
import com.supportai.retrieval.StructureAwareChunker;
import com.supportai.security.SecurityUtils;
import com.supportai.service.AuditService;
import com.supportai.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Ingesting and searching the knowledge base.
 *
 * <p>Search works with the assistant disabled. That is what makes the assistant
 * optional rather than load-bearing: during a provider outage an engineer can
 * still find the runbook, they just have to read it themselves.
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final KbDocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final HybridRetriever retriever;
    private final DocumentMapper mapper;
    private final EmbeddingModel embeddingModel;
    private final AssistantConfig.EmbeddingModelInfo embeddingInfo;
    private final AssistantProperties properties;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public KnowledgeServiceImpl(KbDocumentRepository documents, DocumentChunkRepository chunks,
                                HybridRetriever retriever, DocumentMapper mapper,
                                EmbeddingModel embeddingModel,
                                AssistantConfig.EmbeddingModelInfo embeddingInfo,
                                AssistantProperties properties, AuditService audit,
                                JdbcTemplate jdbc) {
        this.documents = documents;
        this.chunks = chunks;
        this.retriever = retriever;
        this.mapper = mapper;
        this.embeddingModel = embeddingModel;
        this.embeddingInfo = embeddingInfo;
        this.properties = properties;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /**
     * Ingests a document.
     *
     * <p>Chunking is structure-aware — see {@link StructureAwareChunker} for what
     * fixed-size windows do to a numbered recovery procedure.
     *
     * <p>Superseding an existing slug deletes the previous version's chunks. That
     * deletion is the important line in this method: leaving them means the
     * assistant keeps answering from a procedure somebody deliberately corrected,
     * with a citation that resolves to a real document.
     */
    @Override
    @Transactional
    public DocumentResponse upload(DocumentUploadRequest request) {
        String slug = request.slug().strip();
        int nextVersion = documents.currentVersionNumber(slug) + 1;

        KbDocument previous = documents.findBySlugAndCurrentVersionTrue(slug).orElse(null);
        if (previous != null) {
            previous.setCurrentVersion(false);
            documents.save(previous);

            int removed = chunks.deleteByDocumentId(previous.getId());
            log.info("superseding {} v{}: removed {} chunks", slug,
                    previous.getVersionNumber(), removed);
            audit.record(AuditService.DOCUMENT_SUPERSEDED, "KbDocument", slug,
                    "v" + previous.getVersionNumber() + " superseded by v" + nextVersion
                            + ", " + removed + " chunks removed");
        }

        KbDocument document = KbDocument.builder()
                .slug(slug)
                .title(request.title().strip())
                .versionNumber(nextVersion)
                .currentVersion(true)
                .supersedesId(previous == null ? null : previous.getId())
                .classification(request.classification() == null
                        ? Classification.INTERNAL : request.classification())
                .service(request.service())
                .sourceFilename(request.sourceFilename())
                .contentHash(sha256(request.content()))
                // Recorded per document. Vectors from different models are not
                // comparable, and a mismatch degrades silently rather than
                // failing.
                .embeddingModel(embeddingInfo.name())
                .embeddingDimension(embeddingInfo.dimensions())
                .uploadedBy(SecurityUtils.currentUsername())
                .build();
        documents.save(document);

        List<StructureAwareChunker.Chunk> parsed =
                StructureAwareChunker.chunk(document.getTitle(), request.content());

        int ordinal = 0;
        List<DocumentChunk> saved = new ArrayList<>();
        for (StructureAwareChunker.Chunk chunk : parsed) {
            Set<String> literals = LiteralSignals.extract(chunk.content());
            DocumentChunk row = DocumentChunk.builder()
                    .documentId(document.getId())
                    .ordinal(ordinal++)
                    .breadcrumb(chunk.breadcrumb())
                    .content(chunk.content())
                    .charCount(chunk.charCount())
                    .stepCount(chunk.stepCount())
                    .completeProcedure(chunk.completeProcedure())
                    .literalTokens(LiteralSignals.toStored(literals))
                    // The breadcrumb is embedded with the text. "1. Confirm the
                    // primary is healthy" embeds almost identically whichever
                    // runbook it came from; the heading path is what separates
                    // them.
                    .embeddedText(chunk.embeddedText())
                    .build();
            saved.add(chunks.save(row));
        }

        // Embeddings are written by native SQL because the pgvector column type
        // has no JPA mapping here.
        for (DocumentChunk row : saved) {
            float[] vector = embeddingModel.embed(row.getEmbeddedText());
            jdbc.update("UPDATE document_chunks SET embedding = ?::vector WHERE chunk_id = ?",
                    toVectorLiteral(vector), row.getId());
        }

        document.setChunkCount(saved.size());
        documents.save(document);

        audit.record(AuditService.DOCUMENT_UPLOADED, "KbDocument", slug,
                "v" + nextVersion + ", " + saved.size() + " chunks, "
                        + document.getClassification());
        log.info("ingested {} v{}: {} chunks, {} with partial procedures", slug, nextVersion,
                saved.size(), saved.stream().filter(c -> !c.isCompleteProcedure()).count());

        return mapper.toResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse get(Long id) {
        KbDocument document = documents.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("document", id));
        requireVisible(document);
        return mapper.toResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentResponse> list(Pageable pageable) {
        // Filtered by clearance in the query, not afterwards. A post-filter would
        // return short pages with no explanation.
        List<Classification> visible = visibleTo(SecurityUtils.currentClearance());
        return documents.findVisible(visible, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> versionsOf(String slug) {
        return documents.findBySlugOrderByVersionNumberDesc(slug).stream()
                .filter(d -> d.getClassification().isVisibleTo(SecurityUtils.currentClearance()))
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SecurityUtils.requireStaff("Deleting a document");
        KbDocument document = documents.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("document", id));

        int removed = chunks.deleteByDocumentId(id);
        documents.delete(document);

        audit.record(AuditService.DOCUMENT_DELETED, "KbDocument", document.getSlug(),
                "v" + document.getVersionNumber() + ", " + removed + " chunks removed");
        log.info("deleted {} v{} and {} chunks", document.getSlug(),
                document.getVersionNumber(), removed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChunkResponse> search(String query, String service, int topK) {
        List<RetrievedChunk> retrieved = retriever.retrieve(new HybridRetriever.Query(
                query, SecurityUtils.currentClearance(), service,
                Math.min(topK <= 0 ? properties.getRetrievalTopK() : topK, 50),
                properties.getMinSimilarity()));

        return retrieved.stream().map(mapper::toResponse).toList();
    }

    private void requireVisible(KbDocument document) {
        if (!document.getClassification().isVisibleTo(SecurityUtils.currentClearance())) {
            // Not-found rather than forbidden. Confirming that a RESTRICTED
            // document exists is itself a disclosure — the title of a security
            // runbook says what it protects.
            throw ResourceNotFoundException.of("document", document.getId());
        }
    }

    private List<Classification> visibleTo(Classification clearance) {
        List<Classification> visible = new ArrayList<>();
        for (Classification value : Classification.values()) {
            if (value.isVisibleTo(clearance)) {
                visible.add(value);
            }
        }
        return visible;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
