package com.example.lms.repository;

import com.example.lms.entity.Member;
import com.example.lms.entity.MembershipStatus;
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
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByMembershipNumber(String membershipNumber);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Member> findByMembershipStatus(MembershipStatus status, Pageable pageable);

    @Query(value = "select m from Member m where "
            + "lower(m.name) like lower(concat('%', :q, '%')) "
            + "or lower(m.email) like lower(concat('%', :q, '%')) "
            + "or lower(m.membershipNumber) like lower(concat('%', :q, '%'))",
            countQuery = "select count(m) from Member m where "
                    + "lower(m.name) like lower(concat('%', :q, '%')) "
                    + "or lower(m.email) like lower(concat('%', :q, '%')) "
                    + "or lower(m.membershipNumber) like lower(concat('%', :q, '%'))")
    Page<Member> search(@Param("q") String q, Pageable pageable);

    /**
     * Locked read for the issue path.
     *
     * <p>The borrowing limit has the same race as the copy counter: two
     * simultaneous issues to a member holding four books both read four, both
     * decide there is room, and the member leaves with six.
     *
     * <p>Lock order is always book first, then member. Two operations acquiring
     * the same pair in opposite orders deadlock, and the cycle only appears under
     * real concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("select m from Member m where m.id = :id")
    Optional<Member> lockById(@Param("id") Long id);

    long countByMembershipStatus(MembershipStatus status);
}
