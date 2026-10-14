package com.example.lms.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural checks on the repository queries.
 *
 * <p>These are not database tests — there is no MySQL here, and the queries
 * themselves need Testcontainers to be worth anything. What they do catch is a
 * class of mistake that compiles fine and fails at start-up or in production:
 *
 * <ul>
 *   <li>a {@code @Query} with a fetch join and no explicit {@code countQuery},
 *       which Hibernate cannot derive and which throws when the method is first
 *       called with a {@code Pageable}</li>
 *   <li>a lock annotation quietly removed from the methods the concurrency
 *       correctness depends on</li>
 * </ul>
 *
 * <p>Both are the kind of regression a later refactor introduces silently.
 */
class RepositoryQueryContractTest {

    @Test
    @DisplayName("every paginated fetch-join query supplies its own count query")
    void paginatedFetchJoinsHaveCountQueries() {
        Class<?>[] repositories = {
                BookRepository.class, BookIssueRepository.class,
                BookReturnRepository.class, FineRepository.class, MemberRepository.class
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
                            .as("%s.%s paginates a fetch join, so it needs an explicit "
                                    + "countQuery; Hibernate cannot derive one",
                                    repository.getSimpleName(), method.getName())
                            .isNotBlank();
                }
            }
        }
    }

    @Test
    @DisplayName("the issue path still takes pessimistic locks")
    void lockingMethodsAreStillLocked() throws Exception {
        // If either of these loses its @Lock, two concurrent issues of the last
        // copy both succeed and the counter goes wrong with no error anywhere.
        Method lockBook = BookRepository.class.getMethod("lockById", Long.class);
        Method lockMember = MemberRepository.class.getMethod("lockById", Long.class);

        for (Method method : new Method[]{lockBook, lockMember}) {
            org.springframework.data.jpa.repository.Lock lock =
                    method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);

            assertThat(lock)
                    .as("%s must keep its @Lock", method.getName())
                    .isNotNull();
            assertThat(lock.value())
                    .isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        }
    }

    @Test
    @DisplayName("the reservation queue read is locked")
    void queueReadIsLocked() throws Exception {
        // Two concurrent returns of the same title would otherwise both read the
        // same first waiter and both hold a copy for them.
        Method method = ReservationRepository.class.getMethod("lockQueueForBook", Long.class);

        assertThat(method.getAnnotation(org.springframework.data.jpa.repository.Lock.class))
                .isNotNull();
    }

    @Test
    @DisplayName("the return repository exposes the uniqueness check the service relies on")
    void returnUniquenessCheckExists() throws Exception {
        // The service calls this before accepting a return so a double-click gets
        // a readable message rather than a raw constraint violation.
        assertThat(BookReturnRepository.class.getMethod("existsByIssueId", Long.class))
                .isNotNull();
        assertThat(BookReturnRepository.class.getMethod("findByIssueId", Long.class))
                .isNotNull();
    }
}
