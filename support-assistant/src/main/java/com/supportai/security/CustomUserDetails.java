package com.supportai.security;

import com.supportai.entity.Classification;
import com.supportai.entity.Role;
import com.supportai.entity.User;
import com.supportai.util.Constants;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal.
 *
 * <p>Carries the retrieval {@link Classification} clearance, because it is read
 * on every knowledge-base query and a database lookup per search is both slow and
 * a place the check can be forgotten.
 *
 * <p>The user entity is not retained. A principal holding a detached entity
 * invites lazy loads from wherever it is read; everything is copied at
 * construction.
 */
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username;
    private final String displayName;
    private final String password;
    private final Classification clearance;
    private final List<String> roleNames;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public CustomUserDetails(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.password = user.getPasswordHash();
        this.clearance = user.clearance();
        this.roleNames = user.getRoles().stream().map(Role::getName).toList();
        // The ROLE_ prefix is added exactly here. Storing it on the row too gives
        // ROLE_ROLE_LEAD and every check fails as though the role was never
        // granted.
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

    public String getDisplayName() {
        return displayName;
    }

    /** The highest document classification this user may retrieve. */
    public Classification getClearance() {
        return clearance;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public boolean isStaff() {
        return roleNames.contains(Constants.ROLE_ADMIN) || roleNames.contains(Constants.ROLE_LEAD);
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
