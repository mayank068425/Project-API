package com.supportai.security;

import com.supportai.entity.Classification;
import com.supportai.entity.Role;
import com.supportai.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClearanceTest {

    private Role role(String name, String clearance) {
        return Role.builder().id(1L).name(name).maxClassification(clearance).build();
    }

    private User user(Set<Role> roles) {
        return User.builder()
                .id(7L).username("test").email("test@supportai.local").displayName("Test")
                .passwordHash("$2a$12$hash").roles(roles).enabled(true)
                .build();
    }

    @Test
    @DisplayName("the ROLE_ prefix is added once, never stored")
    void prefixAddedExactlyOnce() {
        Role lead = role("LEAD", "RESTRICTED");

        assertThat(lead.getName()).isEqualTo("LEAD");
        assertThat(lead.authority()).isEqualTo("ROLE_LEAD");
        assertThat(lead.authority()).doesNotContain("ROLE_ROLE_");
    }

    @Test
    @DisplayName("clearance is the maximum across roles, so adding one only widens")
    void clearanceIsTheMaximum() {
        // Taking the minimum would mean granting somebody an extra role could
        // silently narrow what they can already read.
        User user = user(Set.of(
                role("VIEWER", "PUBLIC"),
                role("LEAD", "RESTRICTED")));

        assertThat(user.clearance()).isEqualTo(Classification.RESTRICTED);
    }

    @Test
    @DisplayName("classification visibility is ordered")
    void visibilityIsOrdered() {
        assertThat(Classification.PUBLIC.isVisibleTo(Classification.PUBLIC)).isTrue();
        assertThat(Classification.INTERNAL.isVisibleTo(Classification.PUBLIC)).isFalse();
        assertThat(Classification.INTERNAL.isVisibleTo(Classification.INTERNAL)).isTrue();
        assertThat(Classification.RESTRICTED.isVisibleTo(Classification.INTERNAL)).isFalse();
        assertThat(Classification.RESTRICTED.isVisibleTo(Classification.RESTRICTED)).isTrue();
    }

    @Test
    @DisplayName("a user with no roles gets PUBLIC, not everything")
    void noRolesMeansPublic() {
        // The default has to fail closed. A scheduled re-index runs with no
        // principal and must not inherit RESTRICTED access by accident.
        assertThat(user(Set.of()).clearance()).isEqualTo(Classification.PUBLIC);
    }

    @Test
    @DisplayName("the principal carries the clearance, so retrieval needs no query")
    void principalCarriesClearance() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("ENGINEER", "INTERNAL"))));

        assertThat(details.getClearance()).isEqualTo(Classification.INTERNAL);
        assertThat(details.getAuthorities())
                .extracting("authority").containsExactly("ROLE_ENGINEER");
    }

    @Test
    @DisplayName("lockout is time-limited, not permanent")
    void lockoutExpires() {
        // Locking an on-call engineer out permanently during an incident is worse
        // than the guessing attempt it prevents.
        User user = user(Set.of(role("ENGINEER", "INTERNAL")));
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
        User user = user(Set.of(role("ENGINEER", "INTERNAL")));
        user.recordFailure();
        user.recordFailure();
        user.recordSuccess();

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }
}
