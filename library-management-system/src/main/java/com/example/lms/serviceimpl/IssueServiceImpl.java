package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.dto.request.IssueBookRequest;
import com.example.lms.dto.response.IssueResponse;
import com.example.lms.entity.Book;
import com.example.lms.entity.BookIssue;
import com.example.lms.entity.LoanStatus;
import com.example.lms.entity.Member;
import com.example.lms.entity.Reservation;
import com.example.lms.entity.ReservationStatus;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.MemberLimitExceededException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.mapper.IssueMapper;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.BookRepository;
import com.example.lms.repository.MemberRepository;
import com.example.lms.repository.ReservationRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import com.example.lms.service.FineCalculator;
import com.example.lms.service.IssueService;
import com.example.lms.util.Constants;
import com.example.lms.validation.MemberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Issuing books.
 *
 * <h2>Lock ordering</h2>
 * An issue locks both the book row and the member row, and always in that order
 * — book first, then member. Two operations acquiring the same pair in opposite
 * orders deadlock, and the cycle only appears under real concurrency, which is
 * to say in production and not in testing.
 *
 * <h2>Why both are locked at all</h2>
 * {@code availableCopies} and {@code booksOnLoan} are both derived values that
 * get read, checked and written. Read without a lock, two concurrent issues of
 * the last copy both see one available and both decrement; a member at four
 * books gets two more.
 */
@Service
public class IssueServiceImpl implements IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueServiceImpl.class);

    private final BookRepository books;
    private final MemberRepository members;
    private final BookIssueRepository issues;
    private final ReservationRepository reservations;
    private final IssueMapper mapper;
    private final MemberValidator memberValidator;
    private final FineCalculator fineCalculator;
    private final LibraryPolicy policy;
    private final AuditService audit;

    public IssueServiceImpl(BookRepository books, MemberRepository members,
                            BookIssueRepository issues, ReservationRepository reservations,
                            IssueMapper mapper, MemberValidator memberValidator,
                            FineCalculator fineCalculator, LibraryPolicy policy,
                            AuditService audit) {
        this.books = books;
        this.members = members;
        this.issues = issues;
        this.reservations = reservations;
        this.mapper = mapper;
        this.memberValidator = memberValidator;
        this.fineCalculator = fineCalculator;
        this.policy = policy;
        this.audit = audit;
    }

    @Override
    @Transactional
    public IssueResponse issue(IssueBookRequest request) {
        // Book first, then member. Always this order. See the class comment.
        Book book = books.lockById(request.bookId())
                .orElseThrow(() -> ResourceNotFoundException.of("book", request.bookId()));
        Member member = members.lockById(request.memberId())
                .orElseThrow(() -> ResourceNotFoundException.of("member", request.memberId()));

        memberValidator.validateCanBorrow(member);

        if (member.getBooksOnLoan() >= policy.getMaxBooksPerMember()) {
            throw new MemberLimitExceededException(member.getMembershipNumber(),
                    member.getBooksOnLoan(), policy.getMaxBooksPerMember());
        }
        if (issues.memberAlreadyHasTitle(member.getId(), book.getId())) {
            // A limit on total books does not cover this: one member holding two
            // copies of the same title while somebody else waits in the queue is
            // a different problem.
            throw new BusinessRuleException("Member " + member.getMembershipNumber()
                    + " already has a copy of \"" + book.getTitle() + "\" on loan");
        }

        // A held copy is not generally issuable — unless this is the member it is
        // being held for, in which case the hold is consumed rather than a shelf
        // copy, or the head of the queue would be jumping its own queue.
        Reservation readyForThisMember = reservations
                .findByBookIdAndMemberIdAndStatus(book.getId(), member.getId(),
                        ReservationStatus.READY)
                .orElse(null);

        if (readyForThisMember != null) {
            book.collectHeldCopy();
            readyForThisMember.markFulfilled(Instant.now());
            reservations.save(readyForThisMember);
        } else {
            // Throws BookNotAvailableException, naming which of the three reasons
            // applies, because they need different responses at the desk.
            book.takeCopy();
        }

        LocalDate today = fineCalculator.today();
        BookIssue loan = BookIssue.builder()
                .reference(Constants.ISSUE_REFERENCE_PREFIX
                        + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                .book(book)
                .member(member)
                .issueDate(today)
                .dueDate(today.plusDays(policy.getLoanDays()))
                .status(LoanStatus.ISSUED)
                .issuedBy(SecurityUtils.currentUsername())
                .remarks(request.remarks())
                .build();

        member.setBooksOnLoan(member.getBooksOnLoan() + 1);

        books.save(book);
        members.save(member);
        issues.save(loan);

        audit.record(AuditService.BOOK_ISSUED, "BookIssue", loan.getReference(),
                book.getIsbn() + " to " + member.getMembershipNumber()
                        + ", due " + loan.getDueDate());

        log.info("issued {} ({}) to {} until {}", book.getTitle(), book.getIsbn(),
                member.getMembershipNumber(), loan.getDueDate());
        return mapper.toResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse get(Long id) {
        BookIssue loan = issues.findByIdWithRelations(id)
                .orElseThrow(() -> ResourceNotFoundException.of("loan", id));
        // A member may read their own loans; anything else is staff business.
        // Without this any authenticated member could walk the id space and see
        // who has borrowed what, which is a reading-history leak.
        SecurityUtils.requireSelfOrStaff(loan.getMember().getId());
        return mapper.toResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse getByReference(String reference) {
        BookIssue loan = issues.findByReferenceWithRelations(reference)
                .orElseThrow(() -> new ResourceNotFoundException("No loan " + reference));
        SecurityUtils.requireSelfOrStaff(loan.getMember().getId());
        return mapper.toResponse(loan);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IssueResponse> list(LoanStatus status, Long memberId, Long bookId,
                                    Pageable pageable) {
        if (memberId != null) {
            SecurityUtils.requireSelfOrStaff(memberId);
            return issues.findByMember(memberId, pageable).map(mapper::toResponse);
        }
        SecurityUtils.requireStaff("Listing all loans");

        Page<BookIssue> page = bookId != null
                ? issues.findByBook(bookId, pageable)
                : (status != null
                        ? issues.findByStatus(status, pageable)
                        : issues.findAllWithRelations(pageable));
        return page.map(mapper::toResponse);
    }

    /**
     * Labels outstanding loans past their due date.
     *
     * <p>Reporting only. The fine is computed from the dates on demand, so a
     * member's liability is correct whether or not this has run.
     */
    @Override
    @Transactional
    public int markOverdue() {
        LocalDate today = fineCalculator.today();
        List<BookIssue> overdue = issues.findOverdueAsOf(today).stream()
                .filter(loan -> loan.getStatus() == LoanStatus.ISSUED)
                .toList();
        overdue.forEach(loan -> loan.setStatus(LoanStatus.OVERDUE));
        issues.saveAll(overdue);
        return overdue.size();
    }
}
