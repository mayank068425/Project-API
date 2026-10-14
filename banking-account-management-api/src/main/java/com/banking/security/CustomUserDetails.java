package com.banking.security;

import com.banking.entity.Role;
import com.banking.entity.User;
import com.banking.util.Constants;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Carries {@code customerId} alongside the Spring Security contract, because
 * nearly every authorisation question here is "is this your account", and
 * answering it needs the customer id on the principal rather than a database
 * lookup on every request.
 *
 * <p>The user entity is deliberately not retained. A principal holding a detached
 * entity invites lazy loads from wherever it is read, which is the hardest kind
 * of {@code LazyInitializationException} to trace. Everything needed is copied
 * out at construction.
 */
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Long customerId;
    private final List<String> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.customerId = user.customerId();
        this.roleNames = user.getRoles().stream().map(Role::getName).toList();
        // The ROLE_ prefix is added exactly here. Storing it on the row too gives
        // ROLE_ROLE_BANK_EMPLOYEE and every hasRole check fails as though the
        // role had never been granted.
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .map(GrantedAuthority.class::cast)
                .toList();
        this.enabled = user.isEnabled();
        this.accountNonLocked = !user.isLocked();
    }

    public Long getUserId() {
        return userId;
    }

    /** Null for staff logins, who hold no accounts. */
    public Long getCustomerId() {
        return customerId;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public boolean isStaff() {
        return roleNames.contains(Constants.ROLE_ADMIN)
                || roleNames.contains(Constants.ROLE_BANK_EMPLOYEE);
    }

    public boolean isAdmin() {
        return roleNames.contains(Constants.ROLE_ADMIN);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
