package com.example.lms.security;

import com.example.lms.entity.Role;
import com.example.lms.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Carries {@code memberId} alongside the Spring Security contract, because
 * nearly every authorisation question in this system is "is this your record",
 * and answering it needs the member id on the principal rather than a database
 * lookup per request.
 *
 * <p>The user entity is deliberately not held. A principal that keeps a detached
 * entity invites lazy loads from wherever it happens to be read, which is the
 * hardest kind of {@code LazyInitializationException} to trace. Everything
 * needed is copied out at construction.
 */
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Long memberId;
    private final List<String> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.memberId = user.memberId();
        this.roleNames = user.getRoles().stream().map(Role::getName).toList();
        // The ROLE_ prefix is added exactly here. Storing it on the row as well
        // would produce ROLE_ROLE_LIBRARIAN and every hasRole check would fail in
        // a way that looks like the role was never granted.
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

    /** Null for staff logins, which are not library members. */
    public Long getMemberId() {
        return memberId;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public boolean isStaff() {
        return roleNames.contains(com.example.lms.util.Constants.ROLE_ADMIN)
                || roleNames.contains(com.example.lms.util.Constants.ROLE_LIBRARIAN);
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
