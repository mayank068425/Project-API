package com.supportai.security;

import com.supportai.util.Constants;
import com.supportai.util.JwtUtil;
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
 * <p>Registered exactly once. Two filters doing this would authenticate every
 * request twice — harmless for a valid token and confusing when debugging a
 * rejected one.
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

            if (!JwtUtil.isAccessToken(claims) || blacklist.contains(token)) {
                chain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // The user is reloaded rather than trusted from the claims, and
                // the clearance comes from the reloaded roles rather than from the
                // token. A clearance revoked five minutes ago must not keep
                // working for the rest of the token's lifetime.
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
