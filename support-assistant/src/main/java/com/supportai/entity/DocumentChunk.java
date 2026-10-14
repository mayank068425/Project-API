package com.supportai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A retrievable span of a document.
 *
 * <h2>Why chunking is structure-aware rather than fixed-size</h2>
 * Sliding a fixed window over a runbook produces chunks that retrieve beautifully
 * and are dangerous to act on. A window can land so that a chunk contains
 * {@code kubectl rollout restart deploy/payments} with no heading, no procedure
 * context, and <em>without</em> the preceding step warning that restarting first
 * destroys the in-flight transaction log.
 *
 * <p>That chunk is the top hit for "how do I restart the payments pods". The
 * damage is invisible at ingestion and surfaces mid-incident.
 *
 * <h2>The breadcrumb is embedded with the text</h2>
 * "1. Confirm the primary is healthy" embeds almost identically whichever runbook
 * it came from. Prefixing the heading path before embedding is what makes two
 * structurally identical steps from different documents distinguishable.
 */
@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "ix_chunk_document", columnList = "document_id, ordinal"),
        @Index(name = "ix_chunk_procedure", columnList = "complete_procedure")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int ordinal;

    /** The heading path, e.g. "Payments Runbook > 3.2 Recovery Procedure". */
    @Column(length = 500)
    private String breadcrumb;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "char_count", nullable = false)
    @Builder.Default
    private int charCount = 0;

    /** How many numbered steps this chunk contains. Zero for prose. */
    @Column(name = "step_count", nullable = false)
    @Builder.Default
    private int stepCount = 0;

    /**
     * Whether the numbered steps in this chunk are the whole procedure.
     *
     * <p>Surfaced to the caller as a warning when false. A chunk holding steps 4
     * through 7 of a nine-step procedure answers the question asked and omits the
     * part that makes it safe.
     */
    @Column(name = "complete_procedure", nullable = false)
    @Builder.Default
    private boolean completeProcedure = true;

    /**
     * Literal tokens worth matching exactly: error codes, exception class names,
     * pool identifiers, hostnames.
     *
     * <p>Stored as a searchable string rather than derived at query time, because
     * the literal check runs on every search and re-extracting per chunk per query
     * is the same work repeated.
     */
    @Column(name = "literal_tokens", columnDefinition = "text")
    private String literalTokens;

    /**
     * The text actually sent for embedding: breadcrumb plus content.
     *
     * <p>Kept so that a re-index produces byte-identical input, and so a support
     * question about why something did or did not retrieve can be answered.
     */
    @Column(name = "embedded_text", columnDefinition = "text")
    private String embeddedText;

    public String textForPrompt() {
        return (breadcrumb == null || breadcrumb.isBlank() ? "" : breadcrumb + "\n") + content;
    }
}
