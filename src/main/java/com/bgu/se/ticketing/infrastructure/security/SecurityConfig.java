package com.bgu.se.ticketing.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the ticketing platform.
 *
 * <ul>
 *   <li>Stateless session (JWT-based)</li>
 *   <li>BCrypt for password hashing</li>
 *   <li>Public routes: registration, login, H2 console (dev), and event listing</li>
 *   <li>All other routes require authentication</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * BCrypt password encoder bean – available for injection throughout the application.
     *
     * @return a {@link BCryptPasswordEncoder} with the default strength (10 rounds)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures the HTTP security filter chain.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is intentionally disabled: this API is stateless and uses
                // JWT tokens sent via the Authorization header, not cookies.
                // CSRF attacks rely on cookie-based session authentication, so disabling
                // CSRF protection here is safe and is the standard pattern for JWT REST APIs.
                .csrf(AbstractHttpConfigurer::disable)
                // Disable CORS defaults (configure separately if needed)
                .cors(AbstractHttpConfigurer::disable)
                // Stateless session management
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        // Public read-only event browsing
                        .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                        // H2 console (development only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // All other requests require a valid JWT
                        .anyRequest().authenticated()
                )
                // Allow H2 console to render correctly in frames (dev only)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // Add JWT filter before the standard auth filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
