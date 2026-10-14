package com.example.lms.repository;

import com.example.lms.entity.BookIssue;
import com.example.lms.entity.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookIssueRepository extends JpaRepository<BookIssue, Long> {

    @Query("select i from BookIssue i join fetch i.book join fetch i.member "
            + "where i.reference = :reference")
    Optional<BookIssue> findByReferenceWithRelations(@Param("reference") String reference);

    Optional<BookIssue> findByReference(String reference);

    @Query("select i from BookIssue i join fetch i.book join fetch i.member where i.id = :id")
    Optional<BookIssue> findByIdWithRelations(@Param("id") Long id);

    @Query(value = "select i from BookIssue i join fetch i.book join fetch i.member "
            + "where i.member.id = :memberId order by i.issueDate desc",
            countQuery = "select count(i) from BookIssue i where i.member.id = :memberId")
    Page<BookIssue> findByMember(@Param("memberId") Long memberId, Pageable pageable);

    @Query(value = "select i from BookIssue i join fetch i.book join fetch i.member "
            + "where i.book.id = :bookId order by i.issueDate desc",
            countQuery = "select count(i) from BookIssue i where i.book.id = :bookId")
    Page<BookIssue> findByBook(@Param("bookId") Long bookId, Pageable pageable);

    @Query(value = "select i from BookIssue i join fetch i.book join fetch i.member "
            + "where i.status = :status",
            countQuery = "select count(i) from BookIssue i where i.status = :status")
    Page<BookIssue> findByStatus(@Param("status") LoanStatus status, Pageable pageable);

    @Query(value = "select i from BookIssue i join fetch i.book join fetch i.member",
            countQuery = "select count(i) from BookIssue i")
    Page<BookIssue> findAllWithRelations(Pageable pageable);

    @Query("select i from BookIssue i join fetch i.book where i.member.id = :memberId "
            + "and i.status in (com.example.lms.entity.LoanStatus.ISSUED, "
            + "com.example.lms.entity.LoanStatus.OVERDUE)")
    List<BookIssue> findOutstandingForMember(@Param("memberId") Long memberId);

    @Query("select count(i) from BookIssue i where i.member.id = :memberId "
            + "and i.status in (com.example.lms.entity.LoanStatus.ISSUED, "
            + "com.example.lms.entity.LoanStatus.OVERDUE)")
    long countOutstandingForMember(@Param("memberId") Long memberId);

    /**
     * Whether this member already holds this title.
     *
     * <p>A limit on total books does not cover it: one member with two copies of
     * the same title while somebody else waits in the queue is a different
     * problem, and the fix is a different check.
     */
    @Query("select count(i) > 0 from BookIssue i where i.member.id = :memberId "
            + "and i.book.id = :bookId "
            + "and i.status in (com.example.lms.entity.LoanStatus.ISSUED, "
            + "com.example.lms.entity.LoanStatus.OVERDUE)")
    boolean memberAlreadyHasTitle(@Param("memberId") Long memberId,
                                  @Param("bookId") Long bookId);

    @Query("select i from BookIssue i join fetch i.book join fetch i.member "
            + "where i.dueDate < :asOf and i.status in "
            + "(com.example.lms.entity.LoanStatus.ISSUED, com.example.lms.entity.LoanStatus.OVERDUE) "
            + "order by i.dueDate")
    List<BookIssue> findOverdueAsOf(@Param("asOf") LocalDate asOf);

    @Query("select count(i) from BookIssue i where i.issueDate = :day")
    long countIssuedOn(@Param("day") LocalDate day);

    @Query("select count(i) from BookIssue i where i.status in "
            + "(com.example.lms.entity.LoanStatus.ISSUED, com.example.lms.entity.LoanStatus.OVERDUE)")
    long countOutstanding();
}
