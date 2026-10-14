package com.example.lms.entity;

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

    public static final int MAX_FAILED_ATTEMPTS =
            com.example.lms.util.Constants.MAX_FAILED_LOGIN_ATTEMPTS;
    public static final Duration LOCK_DURATION =
            Duration.ofMinutes(com.example.lms.util.Constants.ACCOUNT_LOCK_MINUTES);

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
     * EAGER, and this is the one place it is right.
     *
     * <p>Roles are needed on every authenticated request to build the
     * {@code UserDetails}, and that happens outside any transaction the
     * application opened. Lazy here is a {@code LazyInitializationException} on
     * the first authorisation check, and the usual "fix" for it is switching
     * {@code open-in-view} back on, which trades one bug for a connection leak.
     *
     * <p>The set is small and bounded, so eager costs one join.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    @ToString.Exclude
    private Set<Role> roles = new HashSet<>();

    /**
     * The member record, for member logins. Null for staff.
     *
     * <p>Kept as an association rather than a bare id so a login and its
     * borrowing record cannot point at each other inconsistently.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @ToString.Exclude
    private Member member;

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

    /**
     * Password reset token, stored hashed.
     *
     * <p>A reset token is a temporary credential. Storing it in plaintext hands
     * over every account with a reset in flight to anyone who can read the table.
     */
    @Column(name = "reset_token_hash", length = 100)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private Instant resetTokenExpiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public boolean isLocked() {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    /**
     * Records a failed attempt, locking once the threshold is reached.
     *
     * <p>Time-limited rather than permanent. A permanent lock turns a guessing
     * attempt into a denial of service against the real member, who then has to
     * come to the branch to borrow a novel.
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

    public Long memberId() {
        return member == null ? null : member.getId();
    }

    public boolean resetTokenValid() {
        return resetTokenHash != null && resetTokenExpiresAt != null
                && Instant.now().isBefore(resetTokenExpiresAt);
    }

    public void clearResetToken() {
        this.resetTokenHash = null;
        this.resetTokenExpiresAt = null;
    }
}
