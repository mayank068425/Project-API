package com.example.lms.controller;

import com.example.lms.dto.request.ChangePasswordRequest;
import com.example.lms.dto.request.LoginRequest;
import com.example.lms.dto.request.RegisterRequest;
import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.LoginResponse;
import com.example.lms.service.AuthService;
import com.example.lms.util.Constants;
import com.example.lms.util.JwtUtil;
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
    @Operation(summary = "Self-register as a member",
            description = "Always creates a MEMBER. Staff accounts are created by an "
                    + "administrator, because an endpoint taking a role from the body "
                    + "grants librarian rights to anyone who reads these docs.")
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
            description = "Actually revokes it rather than relying on the client to "
                    + "forget it. A JWT stays valid until it expires, so a no-op logout "
                    + "leaves anyone who captured the token holding a credential.")
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
    public ApiResponse<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.ok("Password changed");
    }

    /**
     * Starts a reset.
     *
     * <p>Answers identically whether or not the address is known, so this cannot
     * be used to check which email addresses hold accounts. In production the
     * token goes out by email; it is echoed here only because there is no mail
     * channel configured, and that is a development affordance rather than the
     * design.
     */
    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(
            @RequestBody Map<String, String> body) {
        String token = authService.beginPasswordReset(body.get("email"));
        return ApiResponse.ok(token == null ? Map.of() : Map.of("developmentToken", token),
                "If that address has an account, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@RequestBody Map<String, String> body) {
        authService.completePasswordReset(body.get("email"), body.get("token"),
                body.get("newPassword"));
        return ApiResponse.ok("Password reset");
    }
}
