package com.jobscheduler.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration — stateless JWT-based auth.
 *
 * ROLE-BASED ACCESS CONTROL:
 * ───────────────────────────
 *   PUBLIC (no token needed):
 *     POST /api/auth/login        → get a JWT token
 *     GET  /swagger-ui.html       → API docs
 *     GET  /api-docs/**           → OpenAPI spec
 *     GET  /actuator/health       → health check
 *
 *   USER role (read-only):
 *     GET  /api/jobs              → list jobs
 *     GET  /api/jobs/{id}         → get job details
 *     GET  /api/jobs/{id}/history → execution history
 *     GET  /api/jobs/stats        → system stats
 *
 *   ADMIN role (full access):
 *     POST /api/jobs              → create job
 *     PUT  /api/jobs/{id}/cancel  → cancel job
 *     PUT  /api/jobs/{id}/pause   → pause job
 *     PUT  /api/jobs/{id}/resume  → resume job
 *
 * WHY STATELESS?
 * Sessions require shared storage across instances.
 * JWT is self-contained — any instance validates any token
 * without a database call. Perfect for distributed systems.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF (not needed for stateless REST APIs) ─────────
            // CSRF protects browser-based session flows. JWT APIs are stateless
            // so CSRF attacks don't apply — each request carries its own auth.
            .csrf(AbstractHttpConfigurer::disable)

            // ── Authorization rules ───────────────────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public endpoints — no token required
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                 "/api-docs", "/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Read-only endpoints — USER and ADMIN both allowed
                .requestMatchers(HttpMethod.GET, "/api/jobs/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/jobs").hasAnyRole("USER", "ADMIN")

                // Write endpoints — ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/jobs").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/jobs/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Stateless session (no server-side sessions) ───────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── Authentication provider ───────────────────────────────────
            .authenticationProvider(authenticationProvider())

            // ── Add JWT filter BEFORE Spring's username/password filter ───
            // JwtAuthenticationFilter runs first, sets authentication in context.
            // If it sets auth successfully, UsernamePasswordAuthenticationFilter
            // sees an already-authenticated context and skips.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * DaoAuthenticationProvider — loads users from DB (via UserDetailsService)
     * and validates passwords using BCrypt.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCryptPasswordEncoder — industry standard for password hashing.
     * BCrypt is slow by design (work factor) — makes brute-force attacks expensive.
     * Never use MD5 or SHA for passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager — used by AuthController to authenticate login requests.
     * Spring Boot auto-configures this from AuthenticationConfiguration.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
