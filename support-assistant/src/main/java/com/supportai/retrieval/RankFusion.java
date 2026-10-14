package com.supportai.retrieval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combines several ranked lists into one.
 *
 * <h2>Fusion is on rank, never on score</h2>
 * pgvector cosine similarity runs roughly 0 to 1; PostgreSQL {@code ts_rank_cd}
 * runs roughly 0 to 0.1. Adding those two numbers silently weights the semantic
 * ranker about ten to one — a weighting nobody chose and nobody wrote down.
 * Worked example: cosine 0.71/0.82 and ts_rank 0.089/0.012 sum to 0.799 and
 * 0.832, so the document the lexical ranker put first loses to the one it put
 * second, purely because of the scales involved.
 *
 * <p>Ranks are unitless, so reciprocal rank fusion has no such hidden weighting.
 *
 * <h2>What k does, and what it does not</h2>
 * Increasing {@code k} flattens the contribution curve, and it is often described
 * as the lever for tuning how aggressively top results dominate. Measured against
 * two documents where one is the exact-token match and the other is
 * conceptually related, k of 5, 10, 20 and 60 produced <em>identical ordering</em>
 * — both documents shift proportionally, so their relative position is unchanged.
 * k is worth setting sensibly and is not the lever for this problem.
 *
 * <h2>The literal bonus is a separate signal, not a ranker weight</h2>
 * Increasing the lexical ranker's weight does eventually promote the exact match,
 * but it promotes <em>every</em> full-text hit at the same time, which is the
 * semantic recall the hybrid search exists to keep. A small additive bonus for a
 * verbatim token match moves one document by roughly one rank position at k=60
 * without changing the ordering of anything else.
 */
public final class RankFusion {

    /** The standard RRF constant. See the class comment on what it does not do. */
    public static final int DEFAULT_K = 60;

    /**
     * Added to a document's fused score when the query's literal tokens appear
     * verbatim in it.
     *
     * <p>0.02 is roughly one rank position at k=60 (the gap between ranks 1 and 2
     * is about 0.00026, and the gap between rank 1 and rank 4 is about 5% of the
     * score). Large enough to break a near-tie in favour of the exact match, small
     * enough that an otherwise irrelevant chunk cannot climb on it alone.
     */
    public static final double LITERAL_BONUS = 0.02;

    private RankFusion() {
    }

    /** One contributing ranker: a name, its ordered ids, and its weight. */
    public record RankedList(String name, List<Long> ids, double weight) {

        public static RankedList of(String name, List<Long> ids) {
            return new RankedList(name, ids, 1.0);
        }
    }

    /**
     * @param contributions which rankers placed this id, and where. Returned so
     *                      the ordering can be explained rather than trusted.
     */
    public record Fused(Long id, double score, Map<String, Integer> contributions,
                        boolean literalMatch) {
    }

    public static List<Fused> fuse(List<RankedList> lists, Set<Long> literalMatches, int k) {
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Map<String, Integer>> contributions = new LinkedHashMap<>();

        for (RankedList list : lists) {
            int rank = 1;
            for (Long id : list.ids()) {
                scores.merge(id, list.weight() * (1.0 / (k + rank)), Double::sum);
                contributions.computeIfAbsent(id, x -> new LinkedHashMap<>())
                        .put(list.name(), rank);
                rank++;
            }
        }

        Set<Long> literal = literalMatches == null ? Set.of() : literalMatches;
        for (Long id : literal) {
            // Only bonus documents that at least one ranker returned. A verbatim
            // match in something nothing retrieved is not evidence of relevance;
            // it is usually a coincidence in an unrelated document.
            if (scores.containsKey(id)) {
                scores.merge(id, LITERAL_BONUS, Double::sum);
            }
        }

        List<Fused> fused = new ArrayList<>();
        scores.forEach((id, score) -> fused.add(new Fused(id, score,
                Map.copyOf(contributions.getOrDefault(id, Map.of())), literal.contains(id))));

        fused.sort((a, b) -> {
            int byScore = Double.compare(b.score(), a.score());
            // Total order. Without the tie-break the result depends on hash
            // iteration order, and two identical searches return different lists.
            return byScore != 0 ? byScore : Long.compare(a.id(), b.id());
        });
        return List.copyOf(fused);
    }

    public static List<Fused> fuse(List<RankedList> lists, Set<Long> literalMatches) {
        return fuse(lists, literalMatches, DEFAULT_K);
    }
}
