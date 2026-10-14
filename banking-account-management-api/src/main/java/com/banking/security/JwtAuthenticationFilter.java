package com.banking.security;

import com.banking.util.Constants;
import com.banking.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Establishes the security context from a bearer token.
 *
 * <p>{@code OncePerRequestFilter}, and registered exactly once. Two filters doing
 * this would authenticate every request twice — harmless for a valid token, and
 * thoroughly confusing when debugging a rejected one, because the log shows two
 * rejections for one call.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklist blacklist;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   CustomUserDetailsService userDetailsService,
                                   TokenBlacklist blacklist) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = JwtUtil.extractBearerToken(
                request.getHeader(Constants.AUTH_HEADER)).orElse(null);

        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = tokenProvider.parse(token);

            // A refresh token must not authenticate an ordinary request, or the
            // long-lived credential silently becomes the short-lived one.
            if (!JwtUtil.isAccessToken(claims) || blacklist.contains(token)) {
                chain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // Reloaded rather than trusted from the claims. A role may have
                // been revoked or the account frozen since the token was issued,
                // and a token that keeps working after that is exactly what this
                // avoids — here it would keep moving money.
                CustomUserDetails details =
                        userDetailsService.loadUserByUsername(claims.getSubject());

                if (details.isEnabled() && details.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    details, null, details.getAuthorities());
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    MDC.put("user", details.getUsername());
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // Cleared rather than rethrown. An invalid token is an
            // unauthenticated request; the entry point turns that into a 401 with
            // a consistent body.
            log.debug("rejected token on {}: {}", request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        } catch (RuntimeException ex) {
            log.warn("could not establish security context on {}", request.getRequestURI(), ex);
            SecurityContextHolder.clearContext();
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("user");
        }
    }
}
