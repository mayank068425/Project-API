package com.banking.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers", indexes = {
        @Index(name = "ux_customer_number", columnList = "customer_number", unique = true),
        @Index(name = "ux_customer_email", columnList = "email", unique = true),
        @Index(name = "ix_customer_last_name", columnList = "last_name"),
        @Index(name = "ix_customer_phone", columnList = "phone")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    private Long id;

    @Column(name = "customer_number", nullable = false, unique = true, length = 20)
    private String customerNumber;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    /**
     * Date of birth, stored. Age is derived.
     *
     * <p>A stored age is correct the day it is written and wrong from the next
     * birthday onwards. Here it gates account opening, so a stale value would
     * eventually let a minor open an unrestricted account.
     */
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 300)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    /**
     * National identifier, e.g. PAN or SSN.
     *
     * <p>Unique, because two customer records sharing one is either a duplicate
     * or fraud. Never returned in a response — see the mapper.
     */
    @Column(name = "national_id", unique = true, length = 40)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    /**
     * Soft delete.
     *
     * <p>A customer record anchors every account and transaction they ever had.
     * Hard-deleting it orphans the ledger and destroys records banks are legally
     * required to retain for years.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Accounts held by this customer.
     *
     * <h2>No remove cascade, deliberately</h2>
     * {@code CascadeType.ALL} on this relationship means deleting one customer
     * deletes their accounts, and a further cascade to transactions would delete
     * the ledger rows those accounts appear in. One HTTP call and the audit trail
     * for a person's entire banking history is gone — the cascade doing exactly
     * what it was told.
     *
     * <p>PERSIST and MERGE only. Deletion is refused while accounts remain open.
     */
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    @ToString.Exclude
    private List<Account> accounts = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Age today, derived. Never persisted, so it cannot go stale. */
    @Transient
    public int getAge() {
        return ageOn(LocalDate.now());
    }

    /** Age as at a given date, which is what makes this testable. */
    public int ageOn(LocalDate on) {
        if (dateOfBirth == null) {
            throw new IllegalStateException("Customer has no date of birth");
        }
        if (on.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException("Date is before the customer's date of birth");
        }
        return Period.between(dateOfBirth, on).getYears();
    }

    @Transient
    public boolean isMinor() {
        return getAge() < 18;
    }

    public boolean canOpenAccount() {
        return active && kycStatus.allowsAccountOpening();
    }

    /** Keeps both sides consistent in memory when an account is added. */
    public void addAccount(Account account) {
        accounts.add(account);
        account.setCustomer(this);
    }
}
