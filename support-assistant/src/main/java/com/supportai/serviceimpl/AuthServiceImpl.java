package com.supportai.serviceimpl;

import com.supportai.dto.request.ChangePasswordRequest;
import com.supportai.dto.request.LoginRequest;
import com.supportai.dto.request.RegisterRequest;
import com.supportai.dto.response.LoginResponse;
import com.supportai.entity.Role;
import com.supportai.entity.User;
import com.supportai.exception.DuplicateResourceException;
import com.supportai.repository.RoleRepository;
import com.supportai.repository.UserRepository;
import com.supportai.security.JwtTokenProvider;
import com.supportai.security.SecurityUtils;
import com.supportai.security.TokenBlacklist;
import com.supportai.service.AuditService;
import com.supportai.service.AuthService;
import com.supportai.util.Constants;
import com.supportai.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist blacklist;
    private final AuditService audit;

    public AuthServiceImpl(UserRepository users, RoleRepository roles,
                           PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                           TokenBlacklist blacklist, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
        this.audit = audit;
    }

    /**
     * Creates an account with the ENGINEER role.
     *
     * <p>Administrative, and the role is not taken from the request. Roles carry
     * the retrieval clearance, so an endpoint accepting one from the body would
     * let anybody grant themselves access to every RESTRICTED runbook.
     */
    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String username = request.username().strip();
        String email = request.email().toLowerCase().strip();

        if (users.existsByUsername(username)) {
            throw new DuplicateResourceException("That username is taken");
        }
        if (users.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with that email already exists");
        }

        Role engineer = roles.findByName(Constants.ROLE_ENGINEER)
                .orElseThrow(() -> new IllegalStateException(
                        "The ENGINEER role is missing from the database"));

        User user = User.builder()
                .username(username)
                .email(email)
                .displayName(request.displayName().strip())
                .passwordHash(passwordEncoder.encode(request.password()))
                .team(request.team())
                .roles(Set.of(engineer))
                .enabled(true)
                .build();
        users.save(user);

        log.info("created account {} with clearance {}", username, user.clearance());
        return issueTokens(user);
    }

    /**
     * Authenticates and issues tokens.
     *
     * <p>Every failure returns the same message, and the hash is computed even for
     * an unknown username so the unknown-user case is not measurably faster to
     * reject.
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = users.findByUsernameWithRoles(request.username()).orElse(null);

        if (user == null) {
            passwordEncoder.encode(request.password());
            throw new BadCredentialsException("Invalid username or password");
        }
        if (user.isLocked()) {
            log.warn("login attempt on locked account {}", user.getUsername());
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailure();
            users.save(user);
            audit.record(AuditService.LOGIN_FAILED, "User", user.getUsername(),
                    user.getFailedAttempts() + " consecutive failures");
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!user.isEnabled()) {
            throw new BadCredentialsException("Invalid username or password");
        }

        user.recordSuccess();
        users.save(user);
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public void logout(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return;
        }
        try {
            Claims claims = tokenProvider.parse(bearerToken);
            blacklist.revoke(bearerToken, claims.getExpiration().toInstant());
        } catch (RuntimeException ex) {
            log.debug("logout with an unparseable token");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (RuntimeException ex) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!JwtUtil.isRefreshToken(claims)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Reloaded rather than trusted from the claims. A clearance revoked since
        // the refresh token was issued must not survive into the new access token.
        User user = users.findByUsernameWithRoles(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        return issueTokens(user, refreshToken);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = users.findByUsername(SecurityUtils.currentUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must differ from the old one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        log.info("password changed for {}", user.getUsername());
    }

    private LoginResponse issueTokens(User user) {
        return issueTokens(user, tokenProvider.createRefreshToken(user.getUsername()));
    }

    private LoginResponse issueTokens(User user, String refreshToken) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        String clearance = user.clearance().name();
        return new LoginResponse(
                tokenProvider.createAccessToken(user.getUsername(), user.getId(),
                        roleNames, clearance),
                refreshToken,
                "Bearer",
                tokenProvider.getAccessValiditySeconds(),
                user.getUsername(),
                user.getDisplayName(),
                roleNames,
                // Returned so a client can explain an empty search result rather
                // than showing "nothing found" for something the user cannot see.
                clearance);
    }
}
