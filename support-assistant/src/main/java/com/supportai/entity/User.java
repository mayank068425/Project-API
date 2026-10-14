package com.supportai.entity;

import com.supportai.util.Constants;
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

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * EAGER, and justified here.
     *
     * <p>Roles carry the retrieval clearance, and clearance is read on every
     * knowledge-base query — outside any transaction the application opened.
     * Lazy here throws on the first search, and the usual "fix" is turning
     * {@code open-in-view} back on.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    @ToString.Exclude
    private Set<Role> roles = new HashSet<>();

    /** The team whose incidents this user works on. Free text; teams change. */
    @Column(length = 80)
    private String team;

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
     * <p>Time-limited. Locking an on-call engineer out permanently during an
     * incident is a worse outcome than the guessing attempt it prevents.
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

    /**
     * The highest classification this user may retrieve.
     *
     * <p>The maximum across their roles, so adding a role can only widen access
     * and never narrow it unexpectedly.
     */
    public Classification clearance() {
        return roles.stream()
                .map(role -> Classification.valueOf(role.getMaxClassification()))
                .max(java.util.Comparator.comparingInt(Classification::getLevel))
                .orElse(Classification.PUBLIC);
    }
}
