package com.example.lms.util;

import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.Optional;

/**
 * Stateless helpers for reading a token that has already been verified.
 *
 * <h2>How this differs from {@code security.JwtTokenProvider}</h2>
 * The provider holds the signing key and is the only thing that creates or
 * verifies a token. This class does neither: it reads claims out of an
 * already-parsed {@link Claims} and pulls a bearer token out of a header string.
 *
 * <p>Keeping them apart means the key lives in exactly one place, and the parts
 * that only need to inspect a token can be plain static methods with no
 * dependency on it. Nothing here can accept an unverified token, because it
 * never sees a raw string it is expected to trust.
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    /** Extracts the token from an Authorization header, if it is a bearer one. */
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

    /** Null for staff accounts, which are not library members. */
    public static Long memberId(Claims claims) {
        Number value = claims.get(Constants.CLAIM_MEMBER_ID, Number.class);
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
