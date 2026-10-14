package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "member", indexes = {
        @Index(name = "ux_member_number", columnList = "membership_number", unique = true),
        @Index(name = "ux_member_email", columnList = "email", unique = true),
        @Index(name = "ix_member_status", columnList = "membership_status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "membership_number", nullable = false, unique = true, length = 20)
    private String membershipNumber;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(name = "membership_date", nullable = false)
    private LocalDate membershipDate;

    @Column(name = "membership_expiry")
    private LocalDate membershipExpiry;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false, length = 20)
    @Builder.Default
    private MembershipStatus membershipStatus = MembershipStatus.ACTIVE;

    /**
     * Live count of books on loan.
     *
     * <p>Derived, like the book's availability, and stored for the same reason:
     * the borrowing-limit check runs on every issue and a COUNT each time is a
     * query the desk waits on. Reconciled by the same report.
     */
    @Column(name = "books_on_loan", nullable = false)
    @Builder.Default
    private int booksOnLoan = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public boolean isExpired() {
        return membershipExpiry != null && LocalDate.now().isAfter(membershipExpiry);
    }

    /**
     * Whether this member may borrow at all.
     *
     * <p>Expiry is evaluated here rather than left to a nightly job, so a
     * membership that lapsed yesterday stops working today even if no job ran.
     */
    public boolean canBorrow() {
        return membershipStatus.allowsBorrowing() && !isExpired();
    }
}
