package com.supportai.retrieval;

import com.supportai.entity.DocumentChunk;
import com.supportai.entity.KbDocument;

import java.util.Map;

/**
 * A chunk that came back from retrieval, with its scoring shown.
 *
 * @param contributions which rankers placed this chunk and at what rank. Carried
 *                      through to the API so a result that looks wrong can be
 *                      explained rather than guessed at — "it was rank 1 lexical
 *                      and rank 14 semantic" is a diagnosis, "it scored 0.31" is
 *                      not.
 * @param literalMatch  whether the query's literal tokens appear verbatim here
 */
public record RetrievedChunk(DocumentChunk chunk, KbDocument document, double fusedScore,
                             Map<String, Integer> contributions, boolean literalMatch) {

    /** The reference the model is asked to cite. */
    public String reference() {
        return com.supportai.util.Constants.CITATION_PREFIX + chunk.getId();
    }

    /** Human-readable source, for showing beside the answer. */
    public String citation() {
        String breadcrumb = chunk.getBreadcrumb();
        return document.citation()
                + (breadcrumb == null || breadcrumb.isBlank() ? "" : " — " + breadcrumb);
    }

    public String textForPrompt() {
        return chunk.textForPrompt();
    }
}
