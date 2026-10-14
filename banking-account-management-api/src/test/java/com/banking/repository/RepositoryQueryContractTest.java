package com.banking.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural checks on the repository queries.
 *
 * <p>Not database tests — the queries themselves need Testcontainers to be worth
 * anything. What these catch is a class of mistake that compiles cleanly and
 * fails later:
 *
 * <ul>
 *   <li>a paginated {@code @Query} with a fetch join and no explicit
 *       {@code countQuery}, which Hibernate cannot derive and which throws the
 *       first time the method is called</li>
 *   <li>a lock annotation quietly dropped from a method the correctness of every
 *       balance depends on</li>
 * </ul>
 *
 * <p>The second is the one that matters here. Losing {@code @Lock} on
 * {@code lockById} does not break a single test that exists — it just means two
 * concurrent withdrawals can both succeed, and nobody finds out until a customer
 * is overdrawn.
 */
class RepositoryQueryContractTest {

    @Test
    @DisplayName("every paginated fetch-join query supplies its own count query")
    void paginatedFetchJoinsHaveCountQueries() {
        Class<?>[] repositories = {
                AccountRepository.class, TransactionRepository.class,
                CustomerRepository.class, BeneficiaryRepository.class
        };

        for (Class<?> repository : repositories) {
            for (Method method : repository.getMethods()) {
                org.springframework.data.jpa.repository.Query query =
                        method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
                if (query == null) {
                    continue;
                }

                boolean paginated = Arrays.stream(method.getParameterTypes())
                        .anyMatch(org.springframework.data.domain.Pageable.class::isAssignableFrom);
                boolean hasFetchJoin = query.value().contains("join fetch");

                if (paginated && hasFetchJoin) {
                    assertThat(query.countQuery())
                            .as("%s.%s paginates a fetch join and needs an explicit "
                                    + "countQuery; Hibernate cannot derive one",
                                    repository.getSimpleName(), method.getName())
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    @DisplayName("the money paths still take pessimistic locks")
    void lockingMethodsAreStillLocked() throws Exception {
        Method[] mustLock = {
                AccountRepository.class.getMethod("lockById", Long.class),
                AccountRepository.class.getMethod("lockByAccountNumber", String.class),
                AccountRepository.class.getMethod("lockByAccountNumbersOrdered",
                        java.util.List.class)
        };

        for (Method method : mustLock) {
            org.springframework.data.jpa.repository.Lock lock =
                    method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);

            assertThat(lock)
                    .as("%s must keep its @Lock, or two concurrent debits can both "
                            + "succeed", method.getName())
                    .isNotNull();
            assertThat(lock.value())
                    .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
    }

    @Test
    @DisplayName("the transfer lock query still orders by primary key")
    void transferLockIsOrdered() throws Exception {
        // The deadlock fix. Two simultaneous transfers between the same pair in
        // opposite directions would otherwise take the locks in opposite orders,
        // and MySQL kills one of them. Sorting makes a cycle unconstructible.
        Method method = AccountRepository.class.getMethod(
                "lockByAccountNumbersOrdered", java.util.List.class);

        org.springframework.data.jpa.repository.Query query =
                method.getAnnotation(org.springframework.data.jpa.repository.Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .as("the ordering is what prevents the deadlock; do not remove it")
                .contains("order by a.id");
    }

    @Test
    @DisplayName("the reconciliation queries the ledger rather than trusting the balance")
    void reconciliationChecksExist() throws Exception {
        // The stored balance is a cached total. A cached value with no
        // cross-check drifts silently, and the first symptom is a customer
        // disputing a statement that does not add up.
        assertThat(AccountRepository.class.getMethod("findUnreconciledAccounts"))
                .isNotNull();
        assertThat(TransactionRepository.class.getMethod("findUnbalancedTransfers"))
                .isNotNull();
    }
}
