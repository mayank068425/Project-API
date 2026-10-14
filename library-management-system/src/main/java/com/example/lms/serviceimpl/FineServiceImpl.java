package com.example.lms.serviceimpl;

import com.example.lms.config.LibraryPolicy;
import com.example.lms.dto.response.FineResponse;
import com.example.lms.entity.BookIssue;
import com.example.lms.entity.Fine;
import com.example.lms.entity.FineStatus;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.FineRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import com.example.lms.service.FineCalculator;
import com.example.lms.service.FineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FineServiceImpl implements FineService {

    private static final Logger log = LoggerFactory.getLogger(FineServiceImpl.class);

    private final FineRepository fines;
    private final BookIssueRepository issues;
    private final FineCalculator calculator;
    private final LibraryPolicy policy;
    private final AuditService audit;

    public FineServiceImpl(FineRepository fines, BookIssueRepository issues,
                           FineCalculator calculator, LibraryPolicy policy,
                           AuditService audit) {
        this.fines = fines;
        this.issues = issues;
        this.calculator = calculator;
        this.policy = policy;
        this.audit = audit;
    }

    /**
     * Everything a member currently owes.
     *
     * <p>Two different things, deliberately. Fines already raised on returned
     * books are settled amounts with a row. Books still out and overdue have no
     * fine row yet — the charge is computed from the dates on demand and grows
     * daily. Returning only the first tells a member with an overdue book at home
     * that they owe nothing, and they find out otherwise at the desk.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FineResponse> pendingFor(Long memberId) {
        SecurityUtils.requireSelfOrStaff(memberId);
        List<FineResponse> pending = new ArrayList<>();

        for (Fine fine : fines.findByMemberIdAndStatus(memberId, FineStatus.PENDING)) {
            String reference = issues.findById(fine.getIssue().getId())
                    .map(BookIssue::getReference).orElse(null);
            pending.add(FineResponse.from(fine, reference, fine.getMember().getName()));
        }

        for (BookIssue loan : issues.findOutstandingForMember(memberId)) {
            BigDecimal replacement = loan.getBook()
                    .effectiveReplacementCost(policy.getDefaultReplacementCost());
            FineCalculator.Assessment assessment =
                    calculator.assessOutstanding(loan.getDueDate(), replacement);

            if (assessment.isChargeable()) {
                // No fine row exists yet, so there is no id. The amount is what
                // would be charged if the book came back today.
                pending.add(new FineResponse(null, loan.getReference(), memberId,
                        loan.getMember().getName(), assessment.chargeableDays(),
                        assessment.ratePerDay(), assessment.calculatedAmount(),
                        assessment.amount(), BigDecimal.ZERO, assessment.amount(),
                        assessment.capped(), FineStatus.PENDING, null, null,
                        assessment.explain() + " Still accruing until returned."));
            }
        }
        return pending;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal outstandingTotal(Long memberId) {
        return pendingFor(memberId).stream()
                .map(FineResponse::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Takes payment.
     *
     * <p>Partial payment is allowed, because a member may not be carrying the
     * full amount. The fine only closes when the outstanding balance reaches
     * zero, so a part payment cannot accidentally clear the debt.
     */
    @Override
    @Transactional
    public FineResponse pay(Long fineId, BigDecimal amount) {
        SecurityUtils.requireStaff("Collecting a fine");

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment must be greater than zero");
        }

        Fine fine = fines.findByIdWithRelations(fineId)
                .orElseThrow(() -> ResourceNotFoundException.of("fine", fineId));
        if (fine.getStatus() != FineStatus.PENDING) {
            throw new BusinessRuleException("That fine is already " + fine.getStatus());
        }

        BigDecimal outstanding = fine.outstanding();
        if (amount.compareTo(outstanding) > 0) {
            // Refused rather than accepted with change. A library counter is not
            // a till, and an overpayment recorded here becomes a credit balance
            // nobody is tracking.
            throw new BusinessRuleException("Payment of " + amount + " exceeds the "
                    + outstanding + " outstanding on this fine");
        }

        fine.setAmountPaid(fine.getAmountPaid().add(amount));
        if (fine.outstanding().compareTo(BigDecimal.ZERO) == 0) {
            fine.setStatus(FineStatus.PAID);
            fine.setPaidAt(Instant.now());
            fine.setCollectedBy(SecurityUtils.currentUsername());
        }
        fines.save(fine);

        audit.record(AuditService.FINE_PAID, "Fine", String.valueOf(fine.getId()),
                amount + " received, " + fine.outstanding() + " outstanding");
        log.info("fine {} paid {}, {} outstanding", fine.getId(), amount, fine.outstanding());

        return FineResponse.from(fine, fine.getIssue().getReference(),
                fine.getMember().getName());
    }

    @Override
    @Transactional
    public FineResponse waive(Long fineId, String reason) {
        SecurityUtils.requireStaff("Waiving a fine");
        if (reason == null || reason.isBlank()) {
            // A waiver with no reason is indistinguishable from a mistake, and
            // this is the one action in the system that makes money disappear.
            throw new IllegalArgumentException("A reason is required to waive a fine");
        }

        Fine fine = fines.findByIdWithRelations(fineId)
                .orElseThrow(() -> ResourceNotFoundException.of("fine", fineId));
        if (fine.getStatus() != FineStatus.PENDING) {
            throw new BusinessRuleException("That fine is already " + fine.getStatus());
        }

        fine.setStatus(FineStatus.WAIVED);
        fine.setWaivedReason(reason);
        fine.setCollectedBy(SecurityUtils.currentUsername());
        fines.save(fine);

        audit.record(AuditService.FINE_WAIVED, "Fine", String.valueOf(fine.getId()),
                fine.getAmount() + " waived: " + reason);
        return FineResponse.from(fine, fine.getIssue().getReference(),
                fine.getMember().getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FineResponse> history(Long memberId, Pageable pageable) {
        SecurityUtils.requireSelfOrStaff(memberId);
        return fines.findByMember(memberId, pageable).map(fine -> FineResponse.from(fine,
                fine.getIssue().getReference(), fine.getMember().getName()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FineResponse> byStatus(FineStatus status, Pageable pageable) {
        SecurityUtils.requireStaff("Listing fines");
        return fines.findByStatus(status, pageable).map(fine -> FineResponse.from(fine,
                fine.getIssue().getReference(), fine.getMember().getName()));
    }
}
