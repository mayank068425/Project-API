package com.example.lms.repository;

import com.example.lms.entity.BookReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface BookReturnRepository extends JpaRepository<BookReturn, Long> {

    /**
     * The return for a loan, if it has one.
     *
     * <p>At most one, guaranteed by the unique constraint on {@code issue_id}.
     * The service checks this before accepting a return so a double-click gets a
     * readable message rather than a constraint violation.
     */
    Optional<BookReturn> findByIssueId(Long issueId);

    boolean existsByIssueId(Long issueId);

    Optional<BookReturn> findByReference(String reference);

    @Query(value = "select r from BookReturn r join fetch r.issue i "
            + "join fetch i.book join fetch i.member order by r.returnedAt desc",
            countQuery = "select count(r) from BookReturn r")
    Page<BookReturn> findAllWithRelations(Pageable pageable);

    @Query(value = "select r from BookReturn r join fetch r.issue i "
            + "join fetch i.book join fetch i.member where i.member.id = :memberId "
            + "order by r.returnedAt desc",
            countQuery = "select count(r) from BookReturn r where r.issue.member.id = :memberId")
    Page<BookReturn> findByMember(@Param("memberId") Long memberId, Pageable pageable);

    long countByReturnedOn(LocalDate day);

    @Query("select count(r) from BookReturn r where r.returnedOn = :day and r.daysLate > 0")
    long countLateReturnsOn(@Param("day") LocalDate day);
}
