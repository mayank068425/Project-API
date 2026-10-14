package com.example.lms.security;

import com.example.lms.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the current principal.
 *
 * <p>The ownership check here is the one most systems of this shape miss. Role
 * checks answer "may you call this endpoint"; they do not answer "is this
 * <em>your</em> record". A member calling {@code /api/members/42/history} has
 * the right role and is reading somebody else's borrowing history, and without
 * this check any authenticated member can walk the id space and see who has read
 * what.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails details)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No authenticated user");
        }
        return details;
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    public static Long currentMemberId() {
        return currentUser().getMemberId();
    }

    public static boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CustomUserDetails details
                && details.isStaff();
    }

    /**
     * Allows staff, or the member themselves. Refuses anyone else.
     *
     * <p>Throws not-found rather than forbidden: answering "forbidden" confirms
     * the record exists, and iterating ids against a 403/404 split enumerates the
     * membership.
     */
    public static void requireSelfOrStaff(Long memberId) {
        if (isStaff()) {
            return;
        }
        Long own = currentUser().getMemberId();
        if (own == null || !own.equals(memberId)) {
            throw new ResourceNotFoundException("No such record");
        }
    }

    public static void requireStaff(String action) {
        if (!isStaff()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    action + " requires librarian privileges");
        }
    }
}
