package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.dto.request.ReturnBookRequest;
import com.example.lms.dto.response.ReturnResponse;
import com.example.lms.entity.Book;
import com.example.lms.entity.BookCondition;
import com.example.lms.entity.BookIssue;
import com.example.lms.entity.BookReturn;
import com.example.lms.entity.Fine;
import com.example.lms.entity.FineStatus;
import com.example.lms.entity.Member;
import com.example.lms.entity.Reservation;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.BookReturnRepository;
import com.example.lms.repository.FineRepository;
import com.example.lms.repository.MemberRepository;
import com.example.lms.repository.ReservationRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import com.example.lms.service.FineCalculator;
import com.example.lms.service.ReturnService;
import com.example.lms.util.Constants;
import com.example.lms.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ReturnServiceImpl implements ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnServiceImpl.class);

    private final BookIssueRepository issues;
    private final BookReturnRepository returns;
    private final BookRepository books;
    private final MemberRepository members;
    private final FineRepository fines;
    private final ReservationRepository reservations;
    private final FineCalculator fineCalculator;
    private final LibraryPolicy policy;
    private final AuditService audit;

    public ReturnServiceImpl(BookIssueRepository issues, BookReturnRepository returns,
                             BookRepository books, MemberRepository members,
                             FineRepository fines, ReservationRepository reservations,
                             FineCalculator fineCalculator, LibraryPolicy policy,
                             AuditService audit) {
        this.issues = issues;
        this.returns = returns;
        this.books = books;
        this.members = members;
        this.fines = fines;
        this.reservations = reservations;
        this.fineCalculator = fineCalculator;
        this.policy = policy;
        this.audit = audit;
    }

    /**
     * Takes a copy back.
     *
     * <p>Three things happen: the fine is assessed, the return event is recorded,
     * and the copy is put somewhere. That last part is the one that is easy to
     * get wrong — see the reservation handling below.
     */
    @Override
    @Transactional
    public ReturnResponse returnBook(ReturnBookRequest request) {
        BookIssue loan = issues.findByReferenceWithRelations(request.reference())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No loan " + request.reference()));

        if (!loan.isOutstanding()) {
            throw new BusinessRuleException("Loan " + request.reference()
                    + " is already " + loan.getStatus());
        }
        // Checked before the constraint fires so a double-click at the desk gets
        // a readable message. The unique index on book_return.issue_id is the
        // actual guarantee: two return rows would put the copy count up twice.
        if (returns.existsByIssueId(loan.getId())) {
            throw new BusinessRuleException("Loan " + request.reference()
                    + " has already been returned");
        }

        Book book = books.lockById(loan.getBook().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No book for this loan"));
        Member member = members.lockById(loan.getMember().getId())
                .orElseThrow(() -> new ResourceNotFoundException("No member for this loan"));

        Instant now = Instant.now();
        // The zone is the library's, taken from policy. Converting in the server's
        // zone instead moves the late/on-time boundary by up to a day.
        LocalDate returnedOn = DateUtil.toLocalDate(now, policy.getZone());

        BigDecimal replacement =
                book.effectiveReplacementCost(policy.getDefaultReplacementCost());
        FineCalculator.Assessment assessment =
                fineCalculator.assess(loan.getDueDate(), returnedOn, replacement);

        BookCondition condition = request.condition() == null
                ? BookCondition.GOOD
                : BookCondition.valueOf(request.condition().toUpperCase());

        loan.markReturned();

        // The copy is back in stock either way; whether it is generally issuable
        // is decided next.
        book.returnCopy();

        boolean held = false;
        String heldFor = null;
        List<Reservation> queue = reservations.lockQueueForBook(book.getId());
        if (!queue.isEmpty()) {
            // Shelving it instead would starve the queue: the next walk-in takes
            // the copy and the people who reserved it are still waiting after
            // every return. Invisible in testing, because a test never has a
            // walk-in racing a queue.
            Reservation first = queue.get(0);
            book.holdCopy();
            first.markReady(now,
                    now.plus(Duration.ofDays(policy.getReservationHoldDays())));
            reservations.save(first);
            held = true;
            heldFor = first.getMember().getMembershipNumber();

            audit.record(AuditService.RESERVATION_READY, "Reservation",
                    String.valueOf(first.getId()),
                    "copy of " + book.getIsbn() + " held for "
                            + first.getMember().getMembershipNumber()
                            + " until " + first.getHoldExpiresAt());
        }

        BookReturn bookReturn = BookReturn.builder()
                .reference(Constants.RETURN_REFERENCE_PREFIX
                        + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                .issue(loan)
                .returnedAt(now)
                .returnedOn(returnedOn)
                .daysLate(assessment.chargeableDays())
                .condition(condition)
                .heldForReservation(held)
                .receivedBy(SecurityUtils.currentUsername())
                .remarks(request.remarks())
                .build();
        returns.save(bookReturn);

        Fine fine = null;
        if (assessment.isChargeable()) {
            // One fine per loan, also by unique index.
            fine = fines.findByIssueId(loan.getId()).orElse(null);
            if (fine == null) {
                fine = Fine.builder()
                        .issue(loan)
                        .member(member)
                        .daysOverdue(assessment.chargeableDays())
                        .ratePerDay(assessment.ratePerDay())
                        .calculatedAmount(assessment.calculatedAmount())
                        .amount(assessment.amount())
                        .capped(assessment.capped())
                        .status(FineStatus.PENDING)
                        .build();
                fines.save(fine);
            }
        }

        member.setBooksOnLoan(Math.max(0, member.getBooksOnLoan() - 1));

        issues.save(loan);
        books.save(book);
        members.save(member);

        audit.record(AuditService.BOOK_RETURNED, "BookReturn", bookReturn.getReference(),
                assessment.explain());
        log.info("returned {} from {}: {}", book.getIsbn(), member.getMembershipNumber(),
                assessment.explain());

        return toResponse(bookReturn, loan, assessment, fine, heldFor);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReturnResponse> list(Long memberId, Pageable pageable) {
        if (memberId != null) {
            SecurityUtils.requireSelfOrStaff(memberId);
            return returns.findByMember(memberId, pageable).map(this::toSimpleResponse);
        }
        SecurityUtils.requireStaff("Listing all returns");
        return returns.findAllWithRelations(pageable).map(this::toSimpleResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnResponse getByReference(String reference) {
        BookReturn bookReturn = returns.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("No return " + reference));
        SecurityUtils.requireSelfOrStaff(bookReturn.getIssue().getMember().getId());
        return toSimpleResponse(bookReturn);
    }

    private ReturnResponse toResponse(BookReturn bookReturn, BookIssue loan,
                                      FineCalculator.Assessment assessment, Fine fine,
                                      String heldFor) {
        return new ReturnResponse(
                bookReturn.getId(), bookReturn.getReference(), loan.getReference(),
                loan.getBook().getTitle(), loan.getMember().getName(),
                bookReturn.getReturnedAt(), bookReturn.getReturnedOn(), loan.getDueDate(),
                bookReturn.getDaysLate(), bookReturn.getCondition().name(),
                fine != null, fine == null ? null : fine.getId(),
                assessment.amount(), assessment.capped(), assessment.explain(),
                bookReturn.isHeldForReservation(), heldFor);
    }

    /** For listings, where the assessment is not recomputed. */
    private ReturnResponse toSimpleResponse(BookReturn bookReturn) {
        BookIssue loan = bookReturn.getIssue();
        return new ReturnResponse(
                bookReturn.getId(), bookReturn.getReference(), loan.getReference(),
                loan.getBook().getTitle(), loan.getMember().getName(),
                bookReturn.getReturnedAt(), bookReturn.getReturnedOn(), loan.getDueDate(),
                bookReturn.getDaysLate(), bookReturn.getCondition().name(),
                false, null, null, false,
                bookReturn.wasLate()
                        ? bookReturn.getDaysLate() + " day(s) late"
                        : "Returned on time",
                bookReturn.isHeldForReservation(), null);
    }
}
