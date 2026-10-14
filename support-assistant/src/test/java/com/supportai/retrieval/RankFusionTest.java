package com.supportai.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fusion behaviour the retrieval design rests on.
 *
 * <p>Document 1 is the exact-token match (it contains {@code ORA-01555}).
 * Document 2 is conceptually related and places respectably in both rankers.
 */
class RankFusionTest {

    private static final List<Long> SEMANTIC = List.of(2L, 3L, 1L, 4L);
    private static final List<Long> LEXICAL = List.of(1L, 2L, 4L, 3L);

    private List<RankFusion.RankedList> lists() {
        return List.of(
                RankFusion.RankedList.of("semantic", SEMANTIC),
                RankFusion.RankedList.of("lexical", LEXICAL));
    }

    @Test
    @DisplayName("without a literal signal, the conceptually related document wins")
    void relatedDocumentWinsWithoutLiteralSignal() {
        // This is the failure the literal bonus exists to fix. Document 2 places
        // respectably in both rankers and beats the exact-token match.
        List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of());

        assertThat(fused.get(0).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("k does not change the ordering")
    void kDoesNotReorder() {
        // k is widely described as the tuning lever for RRF. Measured here it is
        // not: both documents shift proportionally, so their relative position is
        // unchanged at every value.
        for (int k : new int[]{5, 10, 20, 60}) {
            List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of(), k);
            assertThat(fused.get(0).id())
                    .as("ordering at k=%d", k)
                    .isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("a literal token match promotes the exact match")
    void literalBonusPromotesExactMatch() {
        List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of(1L));

        assertThat(fused.get(0).id()).isEqualTo(1L);
        assertThat(fused.get(0).literalMatch()).isTrue();
    }

    @Test
    @DisplayName("the bonus only applies to documents a ranker actually returned")
    void bonusRequiresRetrieval() {
        // A verbatim match in something nothing retrieved is usually a
        // coincidence in an unrelated document, not evidence of relevance.
        List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of(99L));

        assertThat(fused).noneMatch(f -> f.id() == 99L);
    }

    @Test
    @DisplayName("recall is preserved: everything either ranker found survives")
    void recallIsPreserved() {
        List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of());

        assertThat(fused).extracting(RankFusion.Fused::id)
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("contributions are carried through, so an ordering can be explained")
    void contributionsAreExposed() {
        // "rank 1 lexical, rank 3 semantic" is a diagnosis. "score 0.031" is not.
        List<RankFusion.Fused> fused = RankFusion.fuse(lists(), Set.of());

        RankFusion.Fused exact = fused.stream()
                .filter(f -> f.id() == 1L).findFirst().orElseThrow();

        assertThat(exact.contributions()).containsEntry("lexical", 1);
        assertThat(exact.contributions()).containsEntry("semantic", 3);
    }

    @Test
    @DisplayName("ties break deterministically, so two identical searches agree")
    void tiesAreDeterministic() {
        // Without a total order the result depends on hash iteration order.
        List<Long> identical = List.of(7L, 8L);
        List<RankFusion.Fused> first = RankFusion.fuse(List.of(
                RankFusion.RankedList.of("a", identical),
                RankFusion.RankedList.of("b", identical)), Set.of());
        List<RankFusion.Fused> second = RankFusion.fuse(List.of(
                RankFusion.RankedList.of("a", identical),
                RankFusion.RankedList.of("b", identical)), Set.of());

        assertThat(first).extracting(RankFusion.Fused::id)
                .isEqualTo(second.stream().map(RankFusion.Fused::id).toList());
    }
}
