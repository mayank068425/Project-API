package com.example.lms.service;

import com.example.lms.dto.request.ChangePasswordRequest;
import com.example.lms.dto.request.LoginRequest;
import com.example.lms.dto.request.RegisterRequest;
import com.example.lms.dto.response.LoginResponse;

/**
 * Registration, sign-in and credential management.
 *
 * <p>The interface exists so controllers depend on a contract rather than an
 * implementation, and so a test can substitute one without a Spring context.
 * Where an interface would have exactly one implementation forever and no test
 * ever stubs it, it is honestly just indirection — but auth is the layer most
 * likely to be swapped for an external identity provider, so the seam is worth
 * having here.
 */
public interface AuthService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    /** Actually revokes the token rather than trusting the client to forget it. */
    void logout(String bearerToken);

    LoginResponse refresh(String refreshToken);

    void changePassword(ChangePasswordRequest request);

    /**
     * Starts a reset.
     *
     * @return the raw token, or null if the address is unknown. In production
     *         this goes to the email channel and never back through the API;
     *         it is returned so the flow is testable without a mail server, and
     *         the caller must not expose it.
     */
    String beginPasswordReset(String email);

    void completePasswordReset(String email, String token, String newPassword);
}
