package com.banking.config;

import com.banking.dto.response.ApiResponse;
import com.banking.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserDetailsService userDetailsService,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disabled because the API is stateless and token-based: there is
                // no session cookie for an attacker to ride. Re-enable the moment
                // any cookie-based authentication appears.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Creating and listing customers is a bank operation.
                        // Reading one's own record goes through the ownership
                        // check in the service, not through a role.
                        .requestMatchers(HttpMethod.POST, "/api/customers")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/customers")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**")
                            .hasRole("ADMIN")

                        // Opening, freezing and closing accounts are bank actions.
                        // A customer cannot freeze their own account here, and
                        // certainly cannot unfreeze one the bank froze.
                        .requestMatchers(HttpMethod.POST, "/api/accounts")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/accounts/**")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/accounts/**")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")

                        // Cash in and cash out happen at a counter or a machine,
                        // not from a customer's own session. Transfers are the
                        // customer-initiated movement, and they are checked for
                        // ownership in the service.
                        .requestMatchers("/api/transactions/deposit",
                                "/api/transactions/withdraw")
                            .hasAnyRole("BANK_EMPLOYEE", "ADMIN")

                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Identical for a missing, expired and forged token.
                            // Distinguishing them tells an attacker which of the
                            // three they achieved.
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    ApiResponse.error("UNAUTHENTICATED",
                                            "Authentication required",
                                            request.getRequestURI()));
                        })
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(),
                                    ApiResponse.error("ACCESS_DENIED",
                                            "Insufficient privileges",
                                            request.getRequestURI()));
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // On, so a bad username and a bad password are indistinguishable.
        // Off, Spring throws UsernameNotFoundException for one and
        // BadCredentialsException for the other, and login becomes an oracle for
        // which customers bank here.
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12. Deliberately slow: the point of a password hash is that an
        // offline attacker holding the dump cannot try billions of candidates.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
