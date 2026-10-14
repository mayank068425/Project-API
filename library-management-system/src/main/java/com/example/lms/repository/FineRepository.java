package com.example.lms.repository;

import com.example.lms.entity.Fine;
import com.example.lms.entity.FineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FineRepository extends JpaRepository<Fine, Long> {

    Optional<Fine> findByIssueId(Long issueId);

    @Query("select f from Fine f join fetch f.issue i join fetch i.book "
            + "join fetch f.member where f.id = :id")
    Optional<Fine> findByIdWithRelations(@Param("id") Long id);

    @Query(value = "select f from Fine f join fetch f.issue i join fetch i.book "
            + "join fetch f.member where f.member.id = :memberId order by f.createdAt desc",
            countQuery = "select count(f) from Fine f where f.member.id = :memberId")
    Page<Fine> findByMember(@Param("memberId") Long memberId, Pageable pageable);

    @Query(value = "select f from Fine f join fetch f.issue i join fetch i.book "
            + "join fetch f.member where f.status = :status",
            countQuery = "select count(f) from Fine f where f.status = :status")
    Page<Fine> findByStatus(@Param("status") FineStatus status, Pageable pageable);

    List<Fine> findByMemberIdAndStatus(Long memberId, FineStatus status);

    @Query("select coalesce(sum(f.amount - f.amountPaid), 0) from Fine f "
            + "where f.member.id = :memberId "
            + "and f.status = com.example.lms.entity.FineStatus.PENDING")
    BigDecimal outstandingForMember(@Param("memberId") Long memberId);

    @Query("select coalesce(sum(f.amountPaid), 0) from Fine f")
    BigDecimal totalCollected();

    @Query("select coalesce(sum(f.amount - f.amountPaid), 0) from Fine f "
            + "where f.status = com.example.lms.entity.FineStatus.PENDING")
    BigDecimal totalPending();
}
