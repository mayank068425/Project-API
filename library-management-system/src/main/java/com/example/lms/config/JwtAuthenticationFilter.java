package com.example.lms.config;

import com.example.lms.security.JwtFilter;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration-side handle on the JWT filter.
 *
 * <h2>Why this does not extend OncePerRequestFilter</h2>
 * The project layout lists both this class and {@code security.JwtFilter}.
 * Implementing the filter twice and registering both would authenticate every
 * request twice — harmless when the token is valid, and confusing when it is
 * not, because the log shows two rejections for one call.
 *
 * <p>{@link JwtFilter} holds the implementation. This class exists so the
 * {@code config} package has the declaration the layout calls for, and it simply
 * hands that bean to {@link SecurityConfig}. Keeping the filter itself in
 * {@code security} means the package that owns tokens also owns the thing that
 * reads them.
 */
@Configuration
public class JwtAuthenticationFilter {

    private final JwtFilter jwtFilter;

    public JwtAuthenticationFilter(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /** The single filter instance placed in the chain. */
    public JwtFilter filter() {
        return jwtFilter;
    }
}
