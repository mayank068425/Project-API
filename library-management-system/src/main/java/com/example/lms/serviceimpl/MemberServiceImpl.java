package com.example.lms.serviceimpl;

import com.example.lms.dto.request.MemberRequest;
import com.example.lms.dto.response.MemberResponse;
import com.example.lms.entity.Member;
import com.example.lms.entity.MembershipStatus;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.mapper.MemberMapper;
import com.example.lms.repository.MemberRepository;
import com.example.lms.security.SecurityUtils;
import com.example.lms.service.AuditService;
import com.example.lms.service.MemberService;
import com.example.lms.util.Constants;
import com.example.lms.validation.MemberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class MemberServiceImpl implements MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberServiceImpl.class);

    private final MemberRepository members;
    private final MemberMapper mapper;
    private final MemberValidator validator;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public MemberServiceImpl(MemberRepository members, MemberMapper mapper,
                             MemberValidator validator, AuditService audit,
                             JdbcTemplate jdbc) {
        this.members = members;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public MemberResponse register(MemberRequest request) {
        validator.validateForCreate(request);
        return mapper.toResponse(createMember(request.name(), request.email(),
                request.phone(), request.address(),
                request.membershipMonths() == null ? 12 : request.membershipMonths()));
    }

    @Override
    @Transactional
    public Member registerForSelfSignup(String name, String email, String phone,
                                        String address) {
        return createMember(name, email, phone, address, 12);
    }

    private Member createMember(String name, String email, String phone, String address,
                                int months) {
        String normalisedEmail = email.toLowerCase().strip();
        if (members.existsByEmail(normalisedEmail)) {
            throw new DuplicateResourceException("A member with that email already exists");
        }

        LocalDate today = LocalDate.now();
        Member member = Member.builder()
                .membershipNumber(nextMembershipNumber())
                .name(name.strip())
                .email(normalisedEmail)
                .phone(phone.strip())
                .address(address)
                .membershipDate(today)
                .membershipExpiry(today.plusMonths(months))
                .membershipStatus(MembershipStatus.ACTIVE)
                .booksOnLoan(0)
                .build();
        members.save(member);

        audit.record(AuditService.MEMBER_REGISTERED, "Member", member.getMembershipNumber(),
                member.getName() + ", expires " + member.getMembershipExpiry());
        return member;
    }

    /**
     * Issues the next membership number.
     *
     * <p>{@code LAST_INSERT_ID(expr)} rather than {@code MAX(number) + 1}: two
     * concurrent registrations both read the same maximum and both write the
     * number after it. The unique index catches the duplicate so no bad row is
     * stored, but one ordinary registration then fails with a constraint error
     * the counter clerk cannot act on. The increment and the read here are a
     * single atomic row update.
     *
     * <p>Its own transaction, so a number consumed by a registration that later
     * rolls back is skipped rather than reused.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextMembershipNumber() {
        jdbc.update("UPDATE number_sequence SET next_value = LAST_INSERT_ID(next_value + 1) "
                + "WHERE sequence_name = 'MEMBER'");
        Long value = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return String.format(Constants.MEMBER_NUMBER_FORMAT, value == null ? 1 : value);
    }

    @Override
    @Transactional
    public MemberResponse update(Long id, MemberRequest request) {
        SecurityUtils.requireSelfOrStaff(id);
        Member member = requireMember(id);

        if (request.name() != null) {
            member.setName(request.name().strip());
        }
        if (request.phone() != null) {
            member.setPhone(request.phone().strip());
        }
        if (request.address() != null) {
            member.setAddress(request.address());
        }
        // Email is not editable here. It identifies the member and is where an
        // overdue notice goes; changing it needs verification of the new address,
        // which is a separate flow.

        members.save(member);
        audit.record(AuditService.MEMBER_UPDATED, "Member", member.getMembershipNumber(), null);
        return mapper.toResponse(member);
    }

    @Override
    @Transactional
    public void deactivate(Long id, String reason) {
        SecurityUtils.requireStaff("Deactivating a membership");
        Member member = requireMember(id);
        validator.validateForDeactivation(member);

        member.setMembershipStatus(MembershipStatus.CANCELLED);
        members.save(member);

        audit.record(AuditService.MEMBER_STATUS_CHANGED, "Member",
                member.getMembershipNumber(),
                "cancelled" + (reason == null ? "" : ": " + reason));
    }

    @Override
    @Transactional
    public MemberResponse setStatus(Long id, MembershipStatus status, String reason) {
        SecurityUtils.requireStaff("Changing membership status");
        Member member = requireMember(id);
        MembershipStatus before = member.getMembershipStatus();

        member.setMembershipStatus(status);
        members.save(member);

        audit.recordChange(AuditService.MEMBER_STATUS_CHANGED, "Member",
                member.getMembershipNumber(), before.name(),
                status.name() + (reason == null ? "" : " (" + reason + ")"));
        log.info("member {} status {} -> {}", member.getMembershipNumber(), before, status);
        return mapper.toResponse(member);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberResponse get(Long id) {
        SecurityUtils.requireSelfOrStaff(id);
        return mapper.toResponse(requireMember(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MemberResponse> search(String query, MembershipStatus status,
                                       Pageable pageable) {
        SecurityUtils.requireStaff("Listing members");
        Page<Member> page = (query != null && !query.isBlank())
                ? members.search(query.strip(), pageable)
                : (status != null
                        ? members.findByMembershipStatus(status, pageable)
                        : members.findAll(pageable));
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Member requireMember(Long id) {
        return members.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("member", id));
    }
}
