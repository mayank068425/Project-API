package com.supportai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI wiring.
 *
 * <h2>What is deliberately not configured here: a QuestionAnswerAdvisor</h2>
 * Spring AI's stock RAG advisor retrieves and injects context transparently. That
 * is convenient, and it removes the two things this application most needs:
 * control over which chunks a given user is cleared to see, and a record of
 * exactly what was retrieved so citations can be checked afterwards.
 *
 * <p>An advisor that silently chooses the context cannot enforce a clearance and
 * cannot be audited. Retrieval is done explicitly instead — see
 * {@code retrieval.HybridRetriever}.
 */
@Configuration
public class AssistantConfig {

    private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /**
     * Records the embedding model's dimensionality at start-up.
     *
     * <p>Vectors from different embedding models are not comparable. Swapping the
     * model without re-indexing does not fail; it silently returns worse results,
     * because cosine distance between vectors from two different spaces is a
     * number without meaning.
     *
     * <p>Logging it gives an operator something to check against the
     * {@code embedding_model} column on each document, which is otherwise the only
     * place the mismatch is visible.
     */
    @Bean
    public EmbeddingModelInfo embeddingModelInfo(EmbeddingModel embeddingModel) {
        int dimensions = embeddingModel.dimensions();
        String name = embeddingModel.getClass().getSimpleName();
        log.info("embedding model {} produces {}-dimensional vectors; documents indexed "
                + "with a different model must be re-indexed", name, dimensions);
        return new EmbeddingModelInfo(name, dimensions);
    }

    public record EmbeddingModelInfo(String name, int dimensions) {
    }
}
