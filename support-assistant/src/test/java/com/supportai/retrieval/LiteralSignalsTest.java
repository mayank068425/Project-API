package com.supportai.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tokens that must be matched exactly rather than semantically.
 *
 * <p>{@code ORA-01555} embeds into the neighbourhood of "database errors in
 * general", which is exactly where the engineer already is.
 */
class LiteralSignalsTest {

    @Test
    @DisplayName("vendor error codes are extracted")
    void extractsErrorCodes() {
        assertThat(LiteralSignals.extract("Seeing ORA-01555 during the nightly job"))
                .contains("ORA-01555");
    }

    @Test
    @DisplayName("exception class names are extracted, qualified or bare")
    void extractsExceptionNames() {
        Set<String> tokens = LiteralSignals.extract(
                "java.sql.SQLTransientConnectionException: HikariPool-1 connection timed out");

        assertThat(tokens).anyMatch(t -> t.contains("SQLTransientConnectionException"));
        assertThat(tokens).contains("HikariPool-1");
    }

    @Test
    @DisplayName("kubernetes resources are extracted")
    void extractsKubernetesResources() {
        assertThat(LiteralSignals.extract("kubectl rollout restart deploy/payments"))
                .contains("deploy/payments");
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void matchingIsCaseInsensitive() {
        // Logs and documentation disagree on casing far more often than they
        // disagree on the identifier itself.
        assertThat(LiteralSignals.matches(Set.of("ORA-01555"),
                "the ora-01555 snapshot too old error", null)).isTrue();
    }

    @Test
    @DisplayName("matching does not normalise away separators")
    void doesNotNormaliseSeparators() {
        // deploy/payments and deploy/payments-api are different things.
        assertThat(LiteralSignals.matches(Set.of("deploy/payments-api"),
                "restart deploy/payments now", null)).isFalse();
    }

    @Test
    @DisplayName("no query tokens means no literal match")
    void emptyQueryMatchesNothing() {
        assertThat(LiteralSignals.matches(Set.of(), "anything at all", null)).isFalse();
    }

    @Test
    @DisplayName("stored tokens are searched as well as the content")
    void searchesStoredTokens() {
        assertThat(LiteralSignals.matches(Set.of("ORA-01555"),
                "unrelated prose", "ORA-01555 SQLException")).isTrue();
    }
}
