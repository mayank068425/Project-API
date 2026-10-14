package com.supportai.dto.response;

import java.util.List;

/**
 * A verified answer from the knowledge base.
 *
 * <p>There is no "unverified" variant of this record. An answer that fails
 * citation verification is not returned with a warning attached — it is withheld,
 * and the caller gets the matching documentation sections to read directly.
 */
public record AnswerResponse(String text, List<ChunkResponse> sources, List<String> warnings) {
}
