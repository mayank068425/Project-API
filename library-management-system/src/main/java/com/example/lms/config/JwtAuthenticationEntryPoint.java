package com.example.lms.config;

import com.example.lms.dto.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Turns an unauthenticated request into a 401 with the usual body shape.
 *
 * <p>Without this, Spring Security's default returns an empty body or an HTML
 * login page, and a client that parses {@code ApiResponse} everywhere else has
 * to special-case the one status it is most likely to hit.
 *
 * <p>The message is deliberately identical for a missing token, an expired one
 * and a forged one. Distinguishing them tells an attacker which of the three
 * they achieved.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("unauthenticated request to {}: {}", request.getRequestURI(),
                authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error("UNAUTHENTICATED", "Authentication required",
                        request.getRequestURI()));
    }
}
