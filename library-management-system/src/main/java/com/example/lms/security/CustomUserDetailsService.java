package com.example.lms.security;

import com.example.lms.entity.User;
import com.example.lms.exception.ResourceNotFoundException;
import com.example.lms.repository.UserRepository;
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
     * <p>Uses the fetch-joined query so roles and the member link are loaded
     * inside this transaction. Building {@link CustomUserDetails} outside one
     * against a lazily-loaded set is a LazyInitializationException on the first
     * authorisation check, and the usual "fix" is turning {@code open-in-view}
     * back on, which trades this bug for a connection leak.
     */
    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        return new CustomUserDetails(user);
    }

    @Transactional(readOnly = true)
    public User requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("No such user"));
    }
}
