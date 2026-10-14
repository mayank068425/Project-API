package com.supportai.controller;

import com.supportai.dto.request.ChangePasswordRequest;
import com.supportai.dto.request.LoginRequest;
import com.supportai.dto.request.RegisterRequest;
import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.LoginResponse;
import com.supportai.service.AuthService;
import com.supportai.util.Constants;
import com.supportai.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an account",
            description = "Administrative. Roles carry the retrieval clearance, so an "
                    + "endpoint accepting a role from the body would let anyone grant "
                    + "themselves access to every RESTRICTED runbook.")
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
            description = "Actually revokes it. A JWT carries a clearance claim and stays "
                    + "valid until it expires, so a no-op logout leaves anyone who captured "
                    + "the token able to read the knowledge base.")
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
}
