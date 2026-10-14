package com.banking.security;

import com.banking.util.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates and verifies tokens. The only holder of the signing key.
 *
 * <p>{@code util.JwtUtil} reads claims from an already-verified token and holds
 * no key, so nothing outside this class can accept a token that has not been
 * through {@link #parse}.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessValiditySeconds;
    private final long refreshValiditySeconds;

    public JwtTokenProvider(@Value("${banking.jwt.secret}") String secret,
                            @Value("${banking.jwt.access-token-validity-seconds:900}") long access,
                            @Value("${banking.jwt.refresh-token-validity-seconds:604800}") long refresh) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 below 256 bits is forgeable. Failing at start-up beats
            // discovering it from a forged token that moved money.
            throw new IllegalStateException(
                    "banking.jwt.secret must be at least 32 characters, got " + bytes.length);
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessValiditySeconds = access;
        this.refreshValiditySeconds = refresh;
    }

    public String createAccessToken(String username, Long userId, Long customerId,
                                    List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(Constants.CLAIM_USER_ID, userId);
        claims.put(Constants.CLAIM_ROLES, roles);
        claims.put(Constants.CLAIM_TOKEN_TYPE, Constants.TOKEN_TYPE_ACCESS);
        if (customerId != null) {
            claims.put(Constants.CLAIM_CUSTOMER_ID, customerId);
        }
        return build(username, claims, accessValiditySeconds);
    }

    public String createRefreshToken(String username) {
        return build(username,
                Map.of(Constants.CLAIM_TOKEN_TYPE, Constants.TOKEN_TYPE_REFRESH),
                refreshValiditySeconds);
    }

    private String build(String subject, Map<String, Object> claims, long validitySeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validitySeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** Verifies signature and expiry, throwing if either fails. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessValiditySeconds() {
        return accessValiditySeconds;
    }
}
