package com.supportai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * An uploaded runbook, SOP or procedure.
 *
 * <h2>Versioning, and why superseding must delete the old chunks</h2>
 * Documents are replaced, not edited. A new version supersedes the previous one
 * and the previous one's chunks and vectors are <em>deleted</em>.
 *
 * <p>Leaving them is the quiet failure of every RAG system shipped without
 * thinking about updates. The old chunks still match queries, still score well,
 * and still carry a citation pointing at a real document — so the assistant keeps
 * answering from the superseded procedure, confidently, with a reference that
 * checks out. Nobody notices until someone follows the old steps.
 *
 * <h2>Why the embedding model is recorded per document</h2>
 * Vectors from different embedding models are not comparable: cosine distance
 * between vectors from two different spaces is a number without meaning. Swapping
 * models without re-indexing does not fail — it silently returns worse results.
 * Recording the model and dimension makes the mismatch findable.
 */
@Entity
@Table(name = "kb_documents",
        uniqueConstraints = @UniqueConstraint(name = "ux_document_slug_version",
                columnNames = {"slug", "version_number"}),
        indexes = {
                @Index(name = "ix_document_slug", columnList = "slug"),
                @Index(name = "ix_document_current", columnList = "current_version"),
                @Index(name = "ix_document_service", columnList = "service")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    /** Stable across versions. Two versions of one runbook share a slug. */
    @Column(nullable = false, length = 200)
    private String slug;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    private int versionNumber = 1;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private boolean currentVersion = true;

    /** The version this one replaced, so the history is followable. */
    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Classification classification = Classification.INTERNAL;

    /** Which service this documents. Used to scope retrieval during an incident. */
    @Column(length = 100)
    private String service;

    @Column(name = "source_filename", length = 300)
    private String sourceFilename;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "chunk_count", nullable = false)
    @Builder.Default
    private int chunkCount = 0;

    /** See the class comment: mixing models degrades silently. */
    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;

    @Column(name = "uploaded_by", length = 80)
    private String uploadedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public String citation() {
        return title + " v" + versionNumber;
    }
}
