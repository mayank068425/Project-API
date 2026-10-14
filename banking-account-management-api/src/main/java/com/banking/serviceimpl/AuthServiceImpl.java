package com.banking.serviceimpl;

import com.banking.dto.request.ChangePasswordRequest;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.LoginResponse;
import com.banking.entity.Customer;
import com.banking.entity.Role;
import com.banking.entity.User;
import com.banking.exception.DuplicateResourceException;
import com.banking.repository.RoleRepository;
import com.banking.repository.UserRepository;
import com.banking.security.JwtTokenProvider;
import com.banking.security.SecurityUtils;
import com.banking.security.TokenBlacklist;
import com.banking.service.AuthService;
import com.banking.service.CustomerService;
import com.banking.util.Constants;
import com.banking.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist blacklist;

    public AuthServiceImpl(UserRepository users, RoleRepository roles,
                           CustomerService customerService, PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider, TokenBlacklist blacklist) {
        this.users = users;
        this.roles = roles;
        this.customerService = customerService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.blacklist = blacklist;
    }

    /**
     * Creates a login and a customer record together.
     *
     * <p>One transaction. A login with no customer record is somebody who can
     * authenticate and hold nothing; a customer record with no login is somebody
     * the bank knows about who cannot reach their own money. Two separate calls
     * guarantee a fraction of registrations end in one of those states.
     *
     * <p>Always CUSTOMER. Staff accounts are created by an administrator, because
     * an endpoint accepting a role from the body grants bank-employee rights to
     * anyone who reads the docs.
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

        Customer customer = customerService.createForSelfRegistration(
                request.firstName(), request.lastName(), email, request.phone(),
                request.dateOfBirth(), request.address(), request.nationalId());

        Role customerRole = roles.findByName(Constants.ROLE_CUSTOMER)
                .orElseThrow(() -> new IllegalStateException(
                        "The CUSTOMER role is missing from the database"));

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .customer(customer)
                .roles(Set.of(customerRole))
                .enabled(true)
                .passwordChangedAt(Instant.now())
                .build();
        users.save(user);

        log.info("registered {} as customer {}", username, customer.getCustomerNumber());
        return issueTokens(user);
    }

    /**
     * Authenticates and issues tokens.
     *
     * <p>Every failure returns the same message. Distinguishing "no such user"
     * from "wrong password" turns login into an oracle for who banks here, and a
     * locked account is not distinguished either because that also confirms the
     * username is real.
     *
     * <p>The hash is computed even for an unknown username. bcrypt at cost 12
     * takes a few hundred milliseconds; skipping it makes the unknown-user case
     * measurably faster to reject, which is the same oracle by timing.
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
            // Already invalid. Nothing to revoke, and no reason to tell the
            // caller their token was bad.
            log.debug("logout with an unparseable token");
        }
    }

    /**
     * Exchanges a refresh token for a new access token.
     *
     * <p>The user is reloaded rather than trusted from the claims. A role may
     * have been revoked or the account disabled since the refresh token was
     * issued, and a credential that keeps working after that would keep moving
     * money.
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
            // The current password is required even though the caller is already
            // authenticated: it is what stops a stolen session from locking the
            // real customer out of their own account.
            throw new BadCredentialsException("Invalid credentials");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The new password must differ from the old one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        users.save(user);
        log.info("password changed for {}", user.getUsername());
    }

    private LoginResponse issueTokens(User user) {
        return issueTokens(user, tokenProvider.createRefreshToken(user.getUsername()));
    }

    private LoginResponse issueTokens(User user, String refreshToken) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        Customer customer = user.getCustomer();
        return new LoginResponse(
                tokenProvider.createAccessToken(user.getUsername(), user.getId(),
                        user.customerId(), roleNames),
                refreshToken,
                "Bearer",
                tokenProvider.getAccessValiditySeconds(),
                user.getUsername(),
                roleNames,
                user.customerId(),
                customer == null ? null : customer.getCustomerNumber());
    }
}
