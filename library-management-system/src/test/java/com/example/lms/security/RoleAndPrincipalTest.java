package com.example.lms.security;

import com.example.lms.entity.Member;
import com.example.lms.entity.Role;
import com.example.lms.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAndPrincipalTest {

    private Role role(String name) {
        return Role.builder().id(1L).name(name).build();
    }

    private User user(Set<Role> roles, Member member) {
        return User.builder()
                .id(7L).username("priya").email("priya@example.com")
                .passwordHash("$2a$12$hash").roles(roles).member(member).enabled(true)
                .build();
    }

    @Test
    @DisplayName("the ROLE_ prefix is added once, not stored")
    void prefixAddedExactlyOnce() {
        // Stored with the prefix, hasRole("LIBRARIAN") compares against
        // ROLE_ROLE_LIBRARIAN and every authorisation check fails in a way that
        // looks like the role was never granted.
        Role librarian = role("LIBRARIAN");

        assertThat(librarian.getName()).isEqualTo("LIBRARIAN");
        assertThat(librarian.authority()).isEqualTo("ROLE_LIBRARIAN");
        assertThat(librarian.authority()).doesNotContain("ROLE_ROLE_");
    }

    @Test
    @DisplayName("authorities carry the prefix, role names do not")
    void authoritiesAreDistinctFromNames() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("MEMBER")), Member.builder().id(3L).build()));

        assertThat(details.getRoleNames()).containsExactly("MEMBER");
        assertThat(details.getAuthorities())
                .extracting("authority").containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("staff is derived from the roles, not from a flag")
    void staffDerivedFromRoles() {
        assertThat(role("ADMIN").isStaff()).isTrue();
        assertThat(role("LIBRARIAN").isStaff()).isTrue();
        assertThat(role("MEMBER").isStaff()).isFalse();
    }

    @Test
    @DisplayName("the principal carries the member id, so ownership checks need no query")
    void principalCarriesMemberId() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("MEMBER")), Member.builder().id(42L).build()));

        assertThat(details.getMemberId()).isEqualTo(42L);
        assertThat(details.isStaff()).isFalse();
    }

    @Test
    @DisplayName("a staff login has no member id")
    void staffHasNoMemberId() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("LIBRARIAN")), null));

        // Null rather than zero or -1: staff are not library members, and a
        // sentinel value would eventually be compared against a real id.
        assertThat(details.getMemberId()).isNull();
        assertThat(details.isStaff()).isTrue();
    }

    @Test
    @DisplayName("a locked account is not account-non-locked")
    void lockReflectedOnPrincipal() {
        User locked = user(Set.of(role("MEMBER")), Member.builder().id(1L).build());
        locked.setLockedUntil(Instant.now().plusSeconds(600));

        assertThat(new CustomUserDetails(locked).isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("lockout is time-limited, not permanent")
    void lockoutExpires() {
        // Permanent lockout turns a guessing attempt into a denial of service
        // against the real member.
        User user = user(Set.of(role("MEMBER")), Member.builder().id(1L).build());
        for (int i = 0; i < User.MAX_FAILED_ATTEMPTS; i++) {
            user.recordFailure();
        }
        assertThat(user.isLocked()).isTrue();

        user.setLockedUntil(Instant.now().minusSeconds(1));
        assertThat(user.isLocked()).isFalse();
    }

    @Test
    @DisplayName("a successful login clears the failure count")
    void successResetsFailures() {
        User user = user(Set.of(role("MEMBER")), Member.builder().id(1L).build());
        user.recordFailure();
        user.recordFailure();
        user.recordSuccess();

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }
}
