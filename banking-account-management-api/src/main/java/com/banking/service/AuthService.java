package com.banking.service;

import com.banking.dto.request.ChangePasswordRequest;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.LoginResponse;

public interface AuthService {

    /** Creates the login and the customer record together, in one transaction. */
    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    /** Actually revokes the token rather than trusting the client to forget it. */
    void logout(String bearerToken);

    LoginResponse refresh(String refreshToken);

    void changePassword(ChangePasswordRequest request);
}
