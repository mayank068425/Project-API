package com.example.lms.repository;

import com.example.lms.entity.Reservation;
import com.example.lms.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * The queue for a title, oldest first, locked.
     *
     * <p>Locked because two concurrent returns of the same title would otherwise
     * both read the same first waiter and both try to hold a copy for them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.book.id = :bookId "
            + "and r.status = com.example.lms.entity.ReservationStatus.WAITING "
            + "order by r.reservedAt asc")
    List<Reservation> lockQueueForBook(@Param("bookId") Long bookId);

    @Query("select r from Reservation r join fetch r.book join fetch r.member "
            + "where r.book.id = :bookId and r.status in "
            + "(com.example.lms.entity.ReservationStatus.WAITING, "
            + "com.example.lms.entity.ReservationStatus.READY) order by r.reservedAt")
    List<Reservation> findActiveQueue(@Param("bookId") Long bookId);

    Optional<Reservation> findByBookIdAndMemberIdAndStatus(Long bookId, Long memberId,
                                                           ReservationStatus status);

    @Query("select r from Reservation r join fetch r.book join fetch r.member "
            + "where r.member.id = :memberId order by r.reservedAt desc")
    List<Reservation> findByMember(@Param("memberId") Long memberId);

    @Query("select r from Reservation r where "
            + "r.status = com.example.lms.entity.ReservationStatus.READY "
            + "and r.holdExpiresAt < :now")
    List<Reservation> findLapsedHolds(@Param("now") Instant now);

    @Query("select r from Reservation r where "
            + "r.status = com.example.lms.entity.ReservationStatus.WAITING "
            + "and r.expiresAt < :now")
    List<Reservation> findAgedOut(@Param("now") Instant now);

    @Query("select count(r) from Reservation r where r.book.id = :bookId "
            + "and r.status = com.example.lms.entity.ReservationStatus.WAITING")
    long queueLength(@Param("bookId") Long bookId);
}
