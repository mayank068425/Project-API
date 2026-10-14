package com.supportai.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural checks on the repository queries.
 *
 * <p>Not database tests. These catch mistakes that compile cleanly and fail
 * later, or worse, do not fail at all.
 */
class RepositoryQueryContractTest {

    @Test
    @DisplayName("every paginated fetch-join query supplies its own count query")
    void paginatedFetchJoinsHaveCountQueries() {
        Class<?>[] repositories = {
                IncidentRepository.class, TimelineEntryRepository.class,
                KbDocumentRepository.class, DocumentChunkRepository.class,
                AuditEventRepository.class
        };

        for (Class<?> repository : repositories) {
            for (Method method : repository.getMethods()) {
                org.springframework.data.jpa.repository.Query query =
                        method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                if (query == null || query.nativeQuery()) {
                    continue;
                }

                boolean paginated = Arrays.stream(method.getParameterTypes())
                        .anyMatch(org.springframework.data.domain.Pageable.class::isAssignableFrom);

                if (paginated && query.value().contains("join fetch")) {
                    assertThat(query.countQuery())
                            .as("%s.%s paginates a fetch join and needs an explicit countQuery",
                                    repository.getSimpleName(), method.getName())
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    @DisplayName("the evidence query excludes assistant-authored entries")
    void evidenceQueryExcludesAssistantEntries() throws Exception {
        // If this filter is ever dropped, an RCA starts citing its own earlier
        // output as an observation. Nothing fails; the analyses just quietly stop
        // tracing back to anything that happened.
        Method method = TimelineEntryRepository.class.getMethod("findEvidenceFor", Long.class);

        String jpql = method.getAnnotation(
                org.springframework.data.jpa.repository.Query.class).value();

        assertThat(jpql).contains("ASSISTANT");
        assertThat(jpql).contains("<>");
    }

    @Test
    @DisplayName("the timeline is ordered by occurrence, not insertion")
    void timelineIsOrderedByOccurrence() throws Exception {
        // Engineers write up the last twenty minutes once the fire is out.
        // Insertion order is a sequence of events that never happened, and the
        // RCA reasons from it.
        assertThat(TimelineEntryRepository.class
                .getMethod("findByIncidentIdOrderByOccurredAtAsc", Long.class)).isNotNull();
    }

    @Test
    @DisplayName("the precedent query requires a written resolution")
    void precedentQueryRequiresResolution() throws Exception {
        // An incident somebody closed without recording what they did teaches
        // nothing and costs attention in a suggestion list.
        Method method = IncidentRepository.class.getMethod("findSimilarResolved",
                String.class, Long.class, int.class);

        String sql = method.getAnnotation(
                org.springframework.data.jpa.repository.Query.class).value();

        assertThat(sql).contains("resolution_summary IS NOT NULL");
    }

    @Test
    @DisplayName("superseding a document can delete its chunks")
    void chunkDeletionExists() throws Exception {
        // Leaving old chunks is the quiet failure of most RAG systems: they keep
        // matching, keep scoring well, and keep citing a real document.
        Method method = DocumentChunkRepository.class.getMethod("deleteByDocumentId", Long.class);

        assertThat(method.getAnnotation(
                org.springframework.data.jpa.repository.Modifying.class)).isNotNull();
    }
}
