package com.banking.security;

import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the current principal, and answers ownership questions.
 *
 * <p>The ownership check is the one most systems of this shape get wrong. Role
 * checks answer "may you call this endpoint"; they do not answer "is this
 * <em>your</em> account". Without the second, any authenticated customer can
 * walk the account-id space and read balances and statements belonging to
 * strangers — and the endpoint looks perfectly well secured while they do it.
 */
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

    public static Long currentCustomerId() {
        return currentUser().getCustomerId();
    }

    public static boolean isStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CustomUserDetails details
                && details.isStaff();
    }

    /**
     * Allows staff, or the customer themselves.
     *
     * <p>Throws not-found rather than forbidden. Answering "forbidden" confirms
     * the record exists, and iterating ids against a 403/404 split enumerates the
     * bank's customer base.
     */
    public static void requireSelfOrStaff(Long customerId) {
        if (isStaff()) {
            return;
        }
        Long own = currentUser().getCustomerId();
        if (own == null || !own.equals(customerId)) {
            throw new ResourceNotFoundException("No such record");
        }
    }

    /**
     * The caller must own this account, unless they are staff.
     *
     * <p>Used on every debit path. A transfer whose source belongs to somebody
     * else is not an authorisation error to be reported as such — it is an
     * invalid transaction, and saying which account exists would be a disclosure
     * in itself.
     */
    public static void requireAccountOwnership(Long accountOwnerCustomerId) {
        if (isStaff()) {
            return;
        }
        Long own = currentUser().getCustomerId();
        if (own == null || !own.equals(accountOwnerCustomerId)) {
            throw new InvalidTransactionException("That account is not yours",
                    InvalidTransactionException.Reason.ACCOUNT_NOT_OWNED);
        }
    }

    public static void requireStaff(String action) {
        if (!isStaff()) {
            throw new AccessDeniedException(action + " requires bank staff privileges");
        }
    }
}
