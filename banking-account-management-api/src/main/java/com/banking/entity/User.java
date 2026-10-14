package com.banking.entity;

import com.banking.util.Constants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "ux_user_username", columnList = "username", unique = true),
        @Index(name = "ux_user_email", columnList = "email", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public static final int MAX_FAILED_ATTEMPTS = Constants.MAX_FAILED_LOGIN_ATTEMPTS;
    public static final Duration LOCK_DURATION =
            Duration.ofMinutes(Constants.ACCOUNT_LOCK_MINUTES);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * EAGER, and this is the one place it is justified.
     *
     * <p>Roles are needed on every authenticated request to build the
     * {@code UserDetails}, and that happens outside any transaction the
     * application opened. Lazy here throws on the first authorisation check, and
     * the usual "fix" is switching {@code open-in-view} back on — which trades
     * one bug for a connection held open for the whole request.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    @ToString.Exclude
    private Set<Role> roles = new HashSet<>();

    /**
     * The customer record this login belongs to. Null for staff.
     *
     * <p>Bank employees authenticate but hold no accounts, so the link is
     * optional. Every ownership check in the system reads through it.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @ToString.Exclude
    private Customer customer;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Records a failed attempt, locking after the threshold.
     *
     * <p>Time-limited rather than permanent. A permanent lock turns a guessing
     * attempt into a denial of service against the real customer, who then
     * cannot reach their own money.
     */
    public void recordFailure() {
        this.failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plus(LOCK_DURATION);
        }
    }

    public void recordSuccess() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(Role::isStaff);
    }

    public Long customerId() {
        return customer == null ? null : customer.getId();
    }
}
