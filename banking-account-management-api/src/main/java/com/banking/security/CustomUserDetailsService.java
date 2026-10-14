package com.banking.security;

import com.banking.entity.User;
import com.banking.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    /**
     * Loads a principal by username.
     *
     * <p>Uses the fetch-joined query so roles and the customer link load inside
     * this transaction. Building {@link CustomUserDetails} outside one against a
     * lazy set throws on the first authorisation check, and the usual "fix" is
     * turning {@code open-in-view} back on — trading this bug for a connection
     * held for the whole request.
     *
     * <p>The message is the same for an unknown username as for any other
     * failure. Spring's {@code hideUserNotFoundExceptions} handles the rest, but
     * there is no reason to write a more specific message here either.
     */
    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        return new CustomUserDetails(user);
    }
}
