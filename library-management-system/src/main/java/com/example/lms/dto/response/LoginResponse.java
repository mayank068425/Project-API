package com.example.lms.dto.response;

import java.util.List;

/**
 * @param memberId null for staff logins, which are not library members. A client
 *                 uses this to decide whether to show borrowing screens at all.
 */
public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresInSeconds, String username, List<String> roles,
                            Long memberId) {
}
