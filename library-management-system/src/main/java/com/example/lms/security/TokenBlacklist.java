package com.example.lms.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens invalidated by an explicit logout.
 *
 * <h2>Why this exists</h2>
 * A JWT is valid until it expires, by design. A logout that only tells the
 * client to forget its token is not a logout: anyone who captured the token
 * still holds a working credential for the rest of its lifetime.
 *
 * <h2>What it costs</h2>
 * Entries are held keyed by token, with the token's own expiry as eviction —
 * there is no point remembering one the signature check will reject anyway. That
 * bounds the map by the number of logouts within a single token lifetime.
 *
 * <p>It does not survive a restart and is not shared between instances. For a
 * single node that is honest and adequate; running more than one means moving
 * this to Redis, and the interface is deliberately small enough that doing so
 * touches one class.
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

    public int size() {
        return revoked.size();
    }
}
