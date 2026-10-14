package com.example.lms.repository;

import com.example.lms.entity.Book;
import com.example.lms.entity.BookStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    Optional<Book> findByIsbn(String isbn);

    /**
     * A book with its author and category loaded.
     *
     * <p>Both associations are LAZY on the entity, and the response carries their
     * names. Without the fetch join, mapping to a DTO touches them after the
     * transaction has closed, and with {@code open-in-view} off that is a
     * LazyInitializationException rather than a silent extra query.
     */
    @Query("select b from Book b join fetch b.author join fetch b.category where b.id = :id")
    Optional<Book> findByIdWithRelations(@Param("id") Long id);

    /**
     * A page of books with their relations.
     *
     * <p>The count query is supplied separately because Hibernate cannot derive
     * one from a query containing a fetch join. These are {@code @ManyToOne}
     * fetches — one row per book — so pagination is safe; fetching a collection
     * and paginating would load everything into memory and page there.
     */
    @Query(value = "select b from Book b join fetch b.author join fetch b.category",
            countQuery = "select count(b) from Book b")
    Page<Book> findAllWithRelations(Pageable pageable);

    @Query(value = "select b from Book b join fetch b.author join fetch b.category "
            + "where b.availableCopies > 0 and b.status = com.example.lms.entity.BookStatus.ACTIVE",
            countQuery = "select count(b) from Book b where b.availableCopies > 0 "
                    + "and b.status = com.example.lms.entity.BookStatus.ACTIVE")
    Page<Book> findAvailable(Pageable pageable);

    @Query(value = "select b from Book b join fetch b.author join fetch b.category "
            + "where b.availableCopies < b.totalCopies",
            countQuery = "select count(b) from Book b where b.availableCopies < b.totalCopies")
    Page<Book> findWithCopiesOut(Pageable pageable);

    @Query(value = "select b from Book b join fetch b.author join fetch b.category "
            + "where b.category.id = :categoryId",
            countQuery = "select count(b) from Book b where b.category.id = :categoryId")
    Page<Book> findByCategory(@Param("categoryId") Long categoryId, Pageable pageable);

    @Query(value = "select b from Book b join fetch b.author join fetch b.category "
            + "where b.author.id = :authorId",
            countQuery = "select count(b) from Book b where b.author.id = :authorId")
    Page<Book> findByAuthor(@Param("authorId") Long authorId, Pageable pageable);

    @Query(value = "select b from Book b join fetch b.author join fetch b.category "
            + "where lower(b.publisher) = lower(:publisher)",
            countQuery = "select count(b) from Book b "
                    + "where lower(b.publisher) = lower(:publisher)")
    Page<Book> findByPublisher(@Param("publisher") String publisher, Pageable pageable);

    /**
     * Free-text search across the fields a reader types.
     *
     * <p>A LIKE with a leading wildcard cannot use a b-tree index, so this scans.
     * Fine for a catalogue of tens of thousands; past that the answer is a MySQL
     * FULLTEXT index and the query changes shape rather than being tuned. Saying
     * so here beats rediscovering it as a mystery slowdown.
     */
    @Query(value = "select b from Book b join fetch b.author join fetch b.category where "
            + "lower(b.title) like lower(concat('%', :q, '%')) "
            + "or lower(b.isbn) like lower(concat('%', :q, '%')) "
            + "or lower(b.publisher) like lower(concat('%', :q, '%')) "
            + "or lower(b.author.name) like lower(concat('%', :q, '%'))",
            countQuery = "select count(b) from Book b where "
                    + "lower(b.title) like lower(concat('%', :q, '%')) "
                    + "or lower(b.isbn) like lower(concat('%', :q, '%')) "
                    + "or lower(b.publisher) like lower(concat('%', :q, '%')) "
                    + "or lower(b.author.name) like lower(concat('%', :q, '%'))")
    Page<Book> search(@Param("q") String q, Pageable pageable);

    /**
     * Loads a book for update, taking a row lock.
     *
     * <p>The only permitted way to read a book on the issue or return path.
     * Without it, two concurrent issues of the last copy both read
     * {@code availableCopies == 1}, both pass the check, and both decrement —
     * the counter reads zero with two loans outstanding against one copy, and
     * nothing errors.
     *
     * <p>Optimistic locking alone detects that, but only after the work is done,
     * turning every contended issue of a popular title into a retry. The three
     * second timeout stops a stuck transaction parking the lock; the handler maps
     * the timeout to 429 so the desk retries rather than seeing a server error.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select b from Book b where b.id = :id")
    Optional<Book> lockById(@Param("id") Long id);

    long countByStatus(BookStatus status);

    @Query("select coalesce(sum(b.totalCopies), 0) from Book b")
    long sumTotalCopies();

    @Query("select coalesce(sum(b.availableCopies), 0) from Book b")
    long sumAvailableCopies();
}
