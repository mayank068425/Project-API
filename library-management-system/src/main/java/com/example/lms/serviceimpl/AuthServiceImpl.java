package com.example.lms.serviceimpl;

import com.example.lms.dto.request.ChangePasswordRequest;
import com.example.lms.dto.request.LoginRequest;
import com.example.lms.dto.request.RegisterRequest;
import com.example.lms.dto.response.LoginResponse;
import com.example.lms.entity.Member;
import com.example.lms.entity.Role;
import com.example.lms.entity.User;
import com.example.lms.exception.DuplicateResourceException;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.security.JwtTokenProvider;
import com.example.lms.security.SecurityUtils;
import com.example.lms.security.TokenBlacklist;
import com.example.lms.service.AuditService;
import com.example.lms.service.AuthService;
import com.example.lms.service.MemberService;
import com.example.lms.util.Constants;
import com.example.lms.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final Duration RESET_TOKEN_VALIDITY = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist blacklist;
    private final AuditService audit;

    public AuthServiceImpl(UserRepository users, RoleRepository roles,
                           MemberService memberService, PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider, TokenBlacklist blacklist,
                           AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.memberService = memberService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
        this.audit = audit;
    }

    /**
     * Self-registration, creating both a login and a member record.
     *
     * <p>Both in one transaction. A login without a member record is somebody who
     * can authenticate and borrow nothing; a member record without a login is a
     * borrower who cannot see their own account. Two separate calls guarantee a
     * fraction of registrations end up in one of those states.
     *
     * <p>Always produces MEMBER. Staff accounts are created by an administrator,
     * because an endpoint that takes a role from the request body grants
     * librarian rights to anyone who reads the API documentation.
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

        Member member = memberService.registerForSelfSignup(
                request.name(), email, request.phone(), request.address());

        Role memberRole = roles.findByName(Constants.ROLE_MEMBER)
                .orElseThrow(() -> new IllegalStateException(
                        "The MEMBER role is missing from the database"));

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .member(member)
                .roles(Set.of(memberRole))
                .enabled(true)
                .build();
        users.save(user);

        log.info("registered {} as member {}", username, member.getMembershipNumber());
        return issueTokens(user);
    }

    /**
     * Authenticates and issues tokens.
     *
     * <p>Every failure returns the same message. Distinguishing "no such user"
     * from "wrong password" turns the login endpoint into a username oracle, and
     * a locked account is not distinguished either because that also confirms the
     * username is real.
     *
     * <p>The hash is computed even when the user does not exist. bcrypt at cost
     * 12 takes a few hundred milliseconds; skipping it for an unknown username
     * makes that case measurably faster to reject, which is the same oracle by a
     * different route.
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

    /**
     * Invalidates an access token.
     *
     * <p>A JWT is valid until it expires by design, so a logout that only tells
     * the client to forget it leaves anyone who captured the token holding a
     * working credential.
     */
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
            // Already invalid. Nothing to revoke, and no reason to tell the
            // caller their token was bad.
            log.debug("logout with an unparseable token");
        }
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * <p>The user is reloaded rather than trusted from the claims: a role may
     * have been revoked or the account disabled since the refresh token was
     * issued, and a token that keeps working after that is exactly the failure
     * this avoids.
     */
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
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.clearResetToken();
        users.save(user);
        log.info("password changed for {}", user.getUsername());
    }

    /**
     * Starts a password reset.
     *
     * <p>Returns null for an unknown address and the caller answers identically
     * either way. Saying "no account with that address" turns this into a
     * membership-checking tool for anyone holding a list of email addresses.
     *
     * <p>The token is stored hashed, like a password, because it is a temporary
     * credential: plaintext tokens in the table hand over every account with a
     * reset in flight.
     */
    @Override
    @Transactional
    public String beginPasswordReset(String email) {
        User user = users.findByEmail(email.toLowerCase().strip()).orElse(null);
        if (user == null) {
            log.info("password reset requested for an unknown address");
            return null;
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        user.setResetTokenHash(passwordEncoder.encode(token));
        user.setResetTokenExpiresAt(Instant.now().plus(RESET_TOKEN_VALIDITY));
        users.save(user);

        log.info("password reset token issued for {}", user.getUsername());
        return token;
    }

    @Override
    @Transactional
    public void completePasswordReset(String email, String token, String newPassword) {
        User user = users.findByEmail(email.toLowerCase().strip())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired reset token"));

        if (!user.resetTokenValid()
                || !passwordEncoder.matches(token, user.getResetTokenHash())) {
            throw new BadCredentialsException("Invalid or expired reset token");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Single use. Leaving it valid lets the same emailed link be replayed by
        // anyone who later reads the mailbox.
        user.clearResetToken();
        user.recordSuccess();
        users.save(user);
        log.info("password reset completed for {}", user.getUsername());
    }

    private LoginResponse issueTokens(User user) {
        return issueTokens(user, tokenProvider.createRefreshToken(user.getUsername()));
    }

    private LoginResponse issueTokens(User user, String refreshToken) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        return new LoginResponse(
                tokenProvider.createAccessToken(user.getUsername(), user.getId(),
                        user.memberId(), roleNames),
                refreshToken,
                "Bearer",
                tokenProvider.getAccessValiditySeconds(),
                user.getUsername(),
                roleNames,
                user.memberId());
    }
}
