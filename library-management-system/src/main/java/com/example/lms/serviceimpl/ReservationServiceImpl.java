package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.dto.request.ReserveBookRequest;
import com.example.lms.dto.response.ReservationResponse;
import com.example.lms.entity.Book;
import com.example.lms.entity.Member;
import com.example.lms.entity.Reservation;
import com.example.lms.entity.ReservationStatus;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.MemberRepository;
import com.example.lms.repository.ReservationRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import com.example.lms.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ReservationServiceImpl implements ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationServiceImpl.class);

    private final ReservationRepository reservations;
    private final BookRepository books;
    private final MemberRepository members;
    private final BookIssueRepository issues;
    private final LibraryPolicy policy;
    private final AuditService audit;

    public ReservationServiceImpl(ReservationRepository reservations, BookRepository books,
                                  MemberRepository members, BookIssueRepository issues,
                                  LibraryPolicy policy, AuditService audit) {
        this.reservations = reservations;
        this.books = books;
        this.members = members;
        this.issues = issues;
        this.policy = policy;
        this.audit = audit;
    }

    @Override
    @Transactional
    public ReservationResponse reserve(ReserveBookRequest request) {
        // A member reserves for themselves; staff may reserve on someone's behalf
        // at the counter.
        Long memberId = request.memberId() != null && SecurityUtils.isStaff()
                ? request.memberId() : SecurityUtils.currentMemberId();
        if (memberId == null) {
            throw new BusinessRuleException("This login is not attached to a member");
        }

        Book book = books.findById(request.bookId())
                .orElseThrow(() -> ResourceNotFoundException.of("book", request.bookId()));
        Member member = members.findById(memberId)
                .orElseThrow(() -> ResourceNotFoundException.of("member", memberId));

        if (!member.canBorrow()) {
            throw new BusinessRuleException("An inactive membership cannot reserve books");
        }
        if (issues.memberAlreadyHasTitle(memberId, book.getId())) {
            throw new BusinessRuleException("You already have this title on loan");
        }
        reservations.findByBookIdAndMemberIdAndStatus(book.getId(), memberId,
                ReservationStatus.WAITING).ifPresent(existing -> {
            throw new BusinessRuleException("You are already in the queue for this title");
        });

        if (book.getAvailableCopies() > 0) {
            // Reserving something sitting on the shelf produces a queue for a
            // book nobody is waiting for, and confuses the return path.
            throw new BusinessRuleException("Copies of \"" + book.getTitle()
                    + "\" are on the shelf now; borrow it rather than reserving it");
        }

        Instant now = Instant.now();
        Reservation reservation = Reservation.builder()
                .book(book)
                .member(member)
                .status(ReservationStatus.WAITING)
                .reservedAt(now)
                .expiresAt(now.plus(Duration.ofDays(policy.getReservationExpiryDays())))
                .build();
        reservations.save(reservation);

        long position = reservations.queueLength(book.getId());
        audit.record(AuditService.RESERVATION_CREATED, "Reservation",
                String.valueOf(reservation.getId()),
                member.getMembershipNumber() + " queued for " + book.getIsbn()
                        + ", position " + position);

        return toResponse(reservation, position);
    }

    @Override
    @Transactional
    public void cancel(Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> ResourceNotFoundException.of("reservation", reservationId));

        // Scoped to the caller, or a member could cancel somebody else's place in
        // the queue by guessing an id.
        if (!SecurityUtils.isStaff()
                && !reservation.getMember().getId().equals(SecurityUtils.currentMemberId())) {
            throw new ResourceNotFoundException("No such reservation");
        }
        if (!reservation.getStatus().isActive()) {
            throw new BusinessRuleException("That reservation is already "
                    + reservation.getStatus());
        }

        // Cancelling a READY reservation frees the held copy, which must then
        // pass to the next in the queue rather than sit held for nobody.
        if (reservation.getStatus() == ReservationStatus.READY) {
            passHoldOn(reservation);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservations.save(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations() {
        Long memberId = SecurityUtils.currentMemberId();
        if (memberId == null) {
            return List.of();
        }
        return reservations.findByMember(memberId).stream()
                .map(reservation -> toResponse(reservation, 0))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> queueForBook(Long bookId) {
        SecurityUtils.requireStaff("Viewing a reservation queue");
        List<Reservation> queue = reservations.findActiveQueue(bookId);
        return java.util.stream.IntStream.range(0, queue.size())
                .mapToObj(i -> toResponse(queue.get(i), i + 1L))
                .toList();
    }

    /**
     * Moves a hold to the next member, or back onto the shelf.
     *
     * <p>The fallback to general availability only when the queue is empty is the
     * part that matters. Skipping it leaves a copy held for a member who has
     * already gone, and it never becomes borrowable again.
     */
    private void passHoldOn(Reservation lapsed) {
        Book book = books.lockById(lapsed.getBook().getId()).orElse(null);
        if (book == null) {
            return;
        }

        List<Reservation> queue = reservations.lockQueueForBook(book.getId());
        if (queue.isEmpty()) {
            book.releaseHold();
        } else {
            Reservation next = queue.get(0);
            Instant now = Instant.now();
            next.markReady(now, now.plus(Duration.ofDays(policy.getReservationHoldDays())));
            reservations.save(next);
            audit.record(AuditService.RESERVATION_READY, "Reservation",
                    String.valueOf(next.getId()),
                    "hold passed on from reservation " + lapsed.getId());
        }
        books.save(book);
    }

    /**
     * Sweeps lapsed holds and aged-out reservations.
     *
     * <p>Scheduled, but nothing depends on it having run: a lapsed hold is also
     * detected on the next issue attempt. A queue whose correctness rests on a
     * cron job is wrong whenever the job fails, and nobody notices until a member
     * complains they were skipped.
     */
    @Override
    @Transactional
    public int expireLapsed() {
        Instant now = Instant.now();
        int processed = 0;

        for (Reservation lapsed : reservations.findLapsedHolds(now)) {
            passHoldOn(lapsed);
            lapsed.setStatus(ReservationStatus.EXPIRED);
            reservations.save(lapsed);
            processed++;
        }

        for (Reservation aged : reservations.findAgedOut(now)) {
            aged.setStatus(ReservationStatus.EXPIRED);
            reservations.save(aged);
            processed++;
        }

        if (processed > 0) {
            log.info("expired {} reservations or holds", processed);
        }
        return processed;
    }

    private ReservationResponse toResponse(Reservation reservation, long position) {
        return new ReservationResponse(reservation.getId(),
                reservation.getBook().getId(), reservation.getBook().getTitle(),
                reservation.getMember().getId(), reservation.getMember().getName(),
                reservation.getStatus(), reservation.getReservedAt(),
                reservation.getHoldExpiresAt(), reservation.getExpiresAt(), position);
    }
}
