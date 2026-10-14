package com.supportai.dto.response;

import java.util.Map;

/**
 * A search hit.
 *
 * @param completeProcedure false when the chunk holds numbered steps that may not
 *                          be the whole procedure. Surfaced because a chunk with
 *                          steps 4 to 7 of a nine-step recovery answers the
 *                          question and omits the part that makes it safe.
 * @param contributions     which rankers placed it and where, so an odd ordering
 *                          can be diagnosed rather than guessed at
 */
public record ChunkResponse(Long chunkId, String citation, String breadcrumb, String content,
                            boolean completeProcedure, int stepCount, boolean literalMatch,
                            double score, Map<String, Integer> contributions) {
}
