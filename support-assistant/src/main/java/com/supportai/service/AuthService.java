package com.supportai.service;

import com.supportai.dto.request.ChangePasswordRequest;
import com.supportai.dto.request.LoginRequest;
import com.supportai.dto.request.RegisterRequest;
import com.supportai.dto.response.LoginResponse;

public interface AuthService {

    /** Administrative. Roles carry the retrieval clearance. */
    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String bearerToken);

    LoginResponse refresh(String refreshToken);

    void changePassword(ChangePasswordRequest request);
}
