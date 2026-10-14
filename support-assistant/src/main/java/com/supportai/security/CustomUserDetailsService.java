package com.supportai.security;

import com.supportai.entity.User;
import com.supportai.repository.UserRepository;
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
     * <p>Uses the fetch-joined query so roles load inside this transaction. Roles
     * carry the clearance, and computing it outside a transaction against a lazy
     * set throws on the first knowledge search.
     */
    @Override
    @Transactional(readOnly = true)
    public CustomUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = users.findByUsernameWithRoles(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));
        return new CustomUserDetails(user);
    }
}
