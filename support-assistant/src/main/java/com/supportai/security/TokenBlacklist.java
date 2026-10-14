package com.supportai.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens invalidated by an explicit logout.
 *
 * <p>A JWT is valid until it expires by design, so a logout that only tells the
 * client to forget it leaves anyone who captured it holding a working credential
 * — one carrying a clearance claim, against a knowledge base with RESTRICTED
 * runbooks in it.
 *
 * <p>In-memory and not shared between instances. Honest for a single node; the
 * interface is small enough that moving it to Redis touches one class.
 */
@Component
public class TokenBlacklist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String token, Instant expiresAt) {
        revoked.put(token, expiresAt);
    }

    public boolean contains(String token) {
        Instant expiry = revoked.get(token);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            revoked.remove(token);
            return false;
        }
        return true;
    }

    public int evictExpired() {
        Instant now = Instant.now();
        int before = revoked.size();
        revoked.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        return before - revoked.size();
    }
}
