package com.supportai.dto.response;

import java.util.List;

/**
 * @param clearance the highest document classification this user may retrieve.
 *                  Returned so a client can explain an empty search result rather
 *                  than showing "nothing found" for something the user simply
 *                  cannot see.
 */
public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresInSeconds, String username, String displayName,
                            List<String> roles, String clearance) {
}
