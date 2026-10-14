package com.banking.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tokens invalidated by an explicit logout.
 *
 * <p>A JWT is valid until it expires by design, so a logout that only tells the
 * client to forget the token leaves anyone who captured it holding a working
 * credential — which, in this system, moves money.
 *
 * <p>Entries are keyed by token with the token's own expiry as eviction, so the
 * map is bounded by the number of logouts within one token lifetime. It does not
 * survive a restart and is not shared between instances: adequate and honest for
 * a single node, and the interface is small enough that moving it to Redis
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
