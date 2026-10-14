package com.banking.controller;

import com.banking.dto.request.ChangePasswordRequest;
import com.banking.dto.request.LoginRequest;
import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.ApiResponse;
import com.banking.dto.response.LoginResponse;
import com.banking.service.AuthService;
import com.banking.util.Constants;
import com.banking.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register as a customer",
            description = "Creates the login and the customer record together. Always "
                    + "CUSTOMER; staff accounts are created by an administrator. KYC "
                    + "starts as PENDING, so no account can be opened until it is verified.")
    public ResponseEntity<ApiResponse<LoginResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented access token",
            description = "Actually revokes it. A JWT stays valid until it expires, so a "
                    + "logout that only tells the client to forget the token leaves "
                    + "anyone who captured it able to move money.")
    public ApiResponse<String> logout(
            @RequestHeader(value = Constants.AUTH_HEADER, required = false)
            String authorization) {
        authService.logout(JwtUtil.extractBearerToken(authorization).orElse(null));
        return ApiResponse.ok("Signed out");
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(authService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password",
            description = "The current password is required even though you are already "
                    + "authenticated; it is what stops a stolen session locking the real "
                    + "customer out.")
    public ApiResponse<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok("Password changed");
    }
}
