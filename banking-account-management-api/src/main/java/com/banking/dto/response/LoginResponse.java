package com.banking.dto.response;

import java.util.List;

/**
 * @param customerId null for staff logins, who hold no accounts. A client uses
 *                   this to decide whether to show account screens at all.
 */
public record LoginResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresInSeconds, String username, List<String> roles,
                            Long customerId, String customerNumber) {
}
