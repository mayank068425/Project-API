package com.supportai.security;

import com.supportai.entity.Classification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the current principal. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new AccessDeniedException("No authenticated user");
        }
        return details;
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    /**
     * The caller's retrieval clearance.
     *
     * <p>Defaults to PUBLIC when there is no principal — a scheduled re-index has
     * no user, and it must not inherit RESTRICTED access by accident.
     */
    public static Classification currentClearance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return details.getClearance();
        }
        return Classification.PUBLIC;
    }

    public static boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CustomUserDetails details
                && details.isStaff();
    }

    public static void requireStaff(String action) {
        if (!isStaff()) {
            throw new AccessDeniedException(action + " requires lead or admin privileges");
        }
    }
}
