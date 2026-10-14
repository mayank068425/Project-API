package com.banking.security;

import com.banking.entity.Customer;
import com.banking.entity.Role;
import com.banking.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAndPrincipalTest {

    private Role role(String name) {
        return Role.builder().id(1L).name(name).build();
    }

    private User user(Set<Role> roles, Customer customer) {
        return User.builder()
                .id(7L).username("priya").email("priya@example.com")
                .passwordHash("$2a$12$hash").roles(roles).customer(customer).enabled(true)
                .build();
    }

    @Test
    @DisplayName("the ROLE_ prefix is added once, never stored")
    void prefixAddedExactlyOnce() {
        // Stored with the prefix, hasRole("BANK_EMPLOYEE") compares against
        // ROLE_ROLE_BANK_EMPLOYEE and every check fails in a way that looks
        // exactly like the role was never granted.
        Role employee = role("BANK_EMPLOYEE");

        assertThat(employee.getName()).isEqualTo("BANK_EMPLOYEE");
        assertThat(employee.authority()).isEqualTo("ROLE_BANK_EMPLOYEE");
        assertThat(employee.authority()).doesNotContain("ROLE_ROLE_");
    }

    @Test
    @DisplayName("authorities carry the prefix, role names do not")
    void authoritiesAreDistinctFromNames() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("CUSTOMER")), Customer.builder().id(3L).build()));

        assertThat(details.getRoleNames()).containsExactly("CUSTOMER");
        assertThat(details.getAuthorities())
                .extracting("authority").containsExactly("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("staff is derived from roles, not a flag")
    void staffDerivedFromRoles() {
        assertThat(role("ADMIN").isStaff()).isTrue();
        assertThat(role("BANK_EMPLOYEE").isStaff()).isTrue();
        assertThat(role("CUSTOMER").isStaff()).isFalse();
    }

    @Test
    @DisplayName("the principal carries the customer id, so ownership needs no query")
    void principalCarriesCustomerId() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("CUSTOMER")), Customer.builder().id(42L).build()));

        assertThat(details.getCustomerId()).isEqualTo(42L);
        assertThat(details.isStaff()).isFalse();
    }

    @Test
    @DisplayName("a staff login has no customer id")
    void staffHasNoCustomerId() {
        CustomUserDetails details = new CustomUserDetails(
                user(Set.of(role("BANK_EMPLOYEE")), null));

        // Null rather than zero or -1. Staff hold no accounts, and a sentinel
        // value would eventually be compared against a real customer id.
        assertThat(details.getCustomerId()).isNull();
        assertThat(details.isStaff()).isTrue();
    }

    @Test
    @DisplayName("a locked account is not account-non-locked")
    void lockReflectedOnPrincipal() {
        User locked = user(Set.of(role("CUSTOMER")), Customer.builder().id(1L).build());
        locked.setLockedUntil(Instant.now().plusSeconds(600));

        assertThat(new CustomUserDetails(locked).isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("lockout is time-limited, not permanent")
    void lockoutExpires() {
        // Permanent lockout turns a guessing attempt into a denial of service
        // against the real customer, who then cannot reach their own money.
        User user = user(Set.of(role("CUSTOMER")), Customer.builder().id(1L).build());
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
        User user = user(Set.of(role("CUSTOMER")), Customer.builder().id(1L).build());
        user.recordFailure();
        user.recordFailure();
        user.recordSuccess();

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
    }
}
