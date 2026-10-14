package com.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;

/**
 * A saved payee.
 *
 * <h2>Two independent controls, doing different jobs</h2>
 * <b>Verification</b> answers "does this account exist and belong to who you
 * think?". An unverified payee is a destination nobody has confirmed, and a
 * transposed digit in an account number otherwise succeeds silently into a
 * stranger's account.
 *
 * <p><b>The cooling-off window</b> is the anti-fraud control. Someone who takes
 * over a session adds a payee and empties the account in seconds. A few hours at
 * a low cap is the window in which the real customer sees the "beneficiary
 * added" notification and calls the bank.
 *
 * <p>Both are enforced on the <em>transfer</em> path, not only at add time. A
 * rule checked once when the record is created is not a rule.
 */
@Entity
@Table(name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_beneficiary_customer_account",
                columnNames = {"customer_id", "beneficiary_account_number"}),
        indexes = {
                @Index(name = "ix_beneficiary_customer", columnList = "customer_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "beneficiary_id")
    private Long id;

    /** The customer who saved this payee. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @ToString.Exclude
    private Customer customer;

    @Column(name = "beneficiary_account_number", nullable = false, length = 20)
    private String beneficiaryAccountNumber;

    /** As held by the bank, captured at verification rather than typed by the payer. */
    @Column(name = "beneficiary_name", nullable = false, length = 150)
    private String beneficiaryName;

    /** The payer's own label for this payee: "Mum", "Landlord". */
    @Column(length = 80)
    private String nickname;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean verified = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by", length = 80)
    private String verifiedBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public void markVerified(String verifiedBy, String confirmedName) {
        this.verified = true;
        this.verifiedAt = Instant.now();
        this.verifiedBy = verifiedBy;
        // The name is taken from the bank's record, not from what the payer
        // typed. A payer who mistypes both the number and the name would
        // otherwise see a payee that looks entirely correct.
        this.beneficiaryName = confirmedName;
    }

    /** Whether this payee is still inside its post-verification cooling-off window. */
    public boolean isInCoolingPeriod(Duration coolingPeriod, Instant now) {
        if (!verified || verifiedAt == null) {
            return false;
        }
        return now.isBefore(verifiedAt.plus(coolingPeriod));
    }

    public boolean isUsable() {
        return active && verified;
    }
}
