package com.example.lms.validation;

import com.example.lms.dto.request.MemberRequest;
import com.example.lms.entity.Member;
import com.example.lms.exception.BusinessRuleException;
import com.example.lms.repository.BookIssueRepository;
import com.example.lms.repository.FineRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Membership rules that need the database or the member's current state.
 */
@Component
public class MemberValidator {

    private final BookIssueRepository issues;
    private final FineRepository fines;

    public MemberValidator(BookIssueRepository issues, FineRepository fines) {
        this.issues = issues;
        this.fines = fines;
    }

    public void validateForCreate(MemberRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessRuleException("Member name is required");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new BusinessRuleException("Email is required");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new BusinessRuleException("Phone is required");
        }
    }

    /**
     * Whether a membership can be cancelled.
     *
     * <p>Refused while books are out or fines are unpaid. Cancelling first means
     * the library loses its handle on both, and nobody owns reconciling the
     * borrowing counter on a cancelled member.
     */
    public void validateForDeactivation(Member member) {
        long outstanding = issues.countOutstandingForMember(member.getId());
        if (outstanding > 0) {
            throw new BusinessRuleException("Member " + member.getMembershipNumber()
                    + " still has " + outstanding + " book(s) on loan");
        }

        BigDecimal owed = fines.outstandingForMember(member.getId());
        if (owed.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException("Member " + member.getMembershipNumber()
                    + " has " + owed + " in unpaid fines");
        }
    }

    /**
     * Whether this member may borrow at all.
     *
     * <p>Expiry is evaluated on the entity rather than from a status column, so a
     * membership that lapsed yesterday stops working today even if no nightly job
     * has run.
     */
    public void validateCanBorrow(Member member) {
        if (!member.canBorrow()) {
            String why = member.isExpired()
                    ? "membership expired on " + member.getMembershipExpiry()
                    : "membership is " + member.getMembershipStatus();
            throw new BusinessRuleException("Member " + member.getMembershipNumber()
                    + " cannot borrow: " + why);
        }
    }
}
