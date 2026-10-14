package com.supportai.retrieval;

import com.supportai.entity.Classification;
import com.supportai.entity.DocumentChunk;
import com.supportai.entity.KbDocument;
import com.supportai.repository.DocumentChunkRepository;
import com.supportai.repository.KbDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Retrieves chunks by combining three signals.
 *
 * <h2>Why three</h2>
 * Semantic search finds paraphrases and misses identifiers: {@code ORA-01555}
 * embeds into the neighbourhood of "database errors in general", which is exactly
 * where the engineer already is. Full-text finds the identifier and misses the
 * paraphrase. Literal containment confirms the identifier is present verbatim
 * rather than merely stemming to something similar.
 *
 * <h2>Clearance is applied inside each query, not afterwards</h2>
 * This is the part most easily got wrong. Filtering the fused list at the end
 * lets a RESTRICTED chunk occupy a top-k slot and then vanish, so a user without
 * clearance silently receives fewer sources than one with it — and receives no
 * indication that anything was removed. Their answer is quietly worse, and
 * retrieval is the least obvious place anyone looks for that.
 *
 * <p>Every SQL below therefore carries the classification predicate.
 */
@Component
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    /** Each ranker returns this multiple of topK before fusion. */
    private static final int CANDIDATE_MULTIPLIER = 4;

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunks;
    private final KbDocumentRepository documents;

    public HybridRetriever(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                           DocumentChunkRepository chunks, KbDocumentRepository documents) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.chunks = chunks;
        this.documents = documents;
    }

    /**
     * @param clearance the caller's maximum visible classification
     * @param service   optional. Narrows to one service's documentation, which
     *                  removes the most convincing wrong answers: correct
     *                  procedures for the wrong component.
     */
    public record Query(String text, Classification clearance, String service, int topK,
                        double minSimilarity) {
    }

    public List<RetrievedChunk> retrieve(Query query) {
        if (query.text() == null || query.text().isBlank()) {
            return List.of();
        }

        List<String> visible = visibleClassifications(query.clearance());
        int candidates = Math.max(query.topK() * CANDIDATE_MULTIPLIER, query.topK());

        List<Long> semantic = semanticSearch(query, visible, candidates);
        List<Long> lexical = lexicalSearch(query, visible, candidates);

        Set<String> queryTokens = LiteralSignals.extract(query.text());
        Set<Long> union = new LinkedHashSet<>(semantic);
        union.addAll(lexical);
        if (union.isEmpty()) {
            return List.of();
        }

        Map<Long, DocumentChunk> byId = chunks.findByIdIn(new ArrayList<>(union)).stream()
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));

        Set<Long> literalMatches = new LinkedHashSet<>();
        if (!queryTokens.isEmpty()) {
            byId.forEach((id, chunk) -> {
                if (LiteralSignals.matches(queryTokens, chunk.getContent(),
                        chunk.getLiteralTokens())) {
                    literalMatches.add(id);
                }
            });
        }

        // Fusion is on rank, never on score: cosine runs 0-1 and ts_rank_cd runs
        // 0-0.1, so adding them silently weights the semantic ranker ten to one.
        List<RankFusion.Fused> fused = RankFusion.fuse(List.of(
                RankFusion.RankedList.of("semantic", semantic),
                RankFusion.RankedList.of("lexical", lexical)), literalMatches);

        Map<Long, KbDocument> documentsById = documents.findAllById(
                        byId.values().stream().map(DocumentChunk::getDocumentId)
                                .distinct().toList()).stream()
                .collect(Collectors.toMap(KbDocument::getId, Function.identity()));

        List<RetrievedChunk> results = new ArrayList<>();
        for (RankFusion.Fused entry : fused) {
            DocumentChunk chunk = byId.get(entry.id());
            if (chunk == null) {
                continue;
            }
            KbDocument document = documentsById.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            results.add(new RetrievedChunk(chunk, document, entry.score(),
                    entry.contributions(), entry.literalMatch()));
            if (results.size() >= query.topK()) {
                break;
            }
        }

        log.debug("retrieved {} chunks ({} semantic, {} lexical, {} literal) for clearance {}",
                results.size(), semantic.size(), lexical.size(), literalMatches.size(),
                query.clearance());
        return List.copyOf(results);
    }

    /**
     * pgvector cosine distance.
     *
     * <p>{@code <=>} is cosine distance, so similarity is {@code 1 - distance} and
     * the ordering is ascending. Getting that backwards returns the least relevant
     * chunks in a plausible-looking list, which is a bug that does not announce
     * itself.
     */
    private List<Long> semanticSearch(Query query, List<String> visible, int limit) {
        float[] embedding = embeddingModel.embed(query.text());
        String vector = toVectorLiteral(embedding);

        StringBuilder sql = new StringBuilder(
                "SELECT c.chunk_id FROM document_chunks c "
                        + "JOIN kb_documents d ON d.document_id = c.document_id "
                        + "WHERE d.current_version = true "
                        + "  AND d.classification = ANY (?) "
                        + "  AND 1 - (c.embedding <=> ?::vector) >= ? ");
        List<Object> args = new ArrayList<>();
        args.add(visible.toArray(new String[0]));
        args.add(vector);
        args.add(query.minSimilarity());

        if (query.service() != null && !query.service().isBlank()) {
            sql.append("  AND (d.service IS NULL OR d.service = ?) ");
            args.add(query.service());
        }
        sql.append("ORDER BY c.embedding <=> ?::vector LIMIT ?");
        args.add(vector);
        args.add(limit);

        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    /** PostgreSQL full-text, ranked by ts_rank_cd. */
    private List<Long> lexicalSearch(Query query, List<String> visible, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.chunk_id FROM document_chunks c "
                        + "JOIN kb_documents d ON d.document_id = c.document_id "
                        + "WHERE d.current_version = true "
                        + "  AND d.classification = ANY (?) "
                        + "  AND c.content_tsv @@ plainto_tsquery('english', ?) ");
        List<Object> args = new ArrayList<>();
        args.add(visible.toArray(new String[0]));
        args.add(query.text());

        if (query.service() != null && !query.service().isBlank()) {
            sql.append("  AND (d.service IS NULL OR d.service = ?) ");
            args.add(query.service());
        }
        sql.append("ORDER BY ts_rank_cd(c.content_tsv, plainto_tsquery('english', ?)) DESC "
                + "LIMIT ?");
        args.add(query.text());
        args.add(limit);

        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    private List<String> visibleClassifications(Classification clearance) {
        Classification effective = clearance == null ? Classification.PUBLIC : clearance;
        List<String> visible = new ArrayList<>();
        for (Classification value : Classification.values()) {
            if (value.isVisibleTo(effective)) {
                visible.add(value.name());
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
}
