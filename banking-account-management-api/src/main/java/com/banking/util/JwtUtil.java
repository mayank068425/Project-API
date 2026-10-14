package com.banking.util;

import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.Optional;

/**
 * Reads a token that has already been verified.
 *
 * <p>Distinct from {@code security.JwtTokenProvider}, which holds the signing
 * key and is the only thing that creates or validates a token. Nothing here can
 * accept an unverified token, because it never sees a raw string it is expected
 * to trust — it only takes an already-parsed {@link Claims}.
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    public static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(Constants.BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(Constants.BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    public static Long userId(Claims claims) {
        Number value = claims.get(Constants.CLAIM_USER_ID, Number.class);
        return value == null ? null : value.longValue();
    }

    /** Null for staff logins, which are not bank customers. */
    public static Long customerId(Claims claims) {
        Number value = claims.get(Constants.CLAIM_CUSTOMER_ID, Number.class);
        return value == null ? null : value.longValue();
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles(Claims claims) {
        Object value = claims.get(Constants.CLAIM_ROLES);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    public static boolean isAccessToken(Claims claims) {
        return Constants.TOKEN_TYPE_ACCESS.equals(
                claims.get(Constants.CLAIM_TOKEN_TYPE, String.class));
    }

    public static boolean isRefreshToken(Claims claims) {
        return Constants.TOKEN_TYPE_REFRESH.equals(
                claims.get(Constants.CLAIM_TOKEN_TYPE, String.class));
    }
}
