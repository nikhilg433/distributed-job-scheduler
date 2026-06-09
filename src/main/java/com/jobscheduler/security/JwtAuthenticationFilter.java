package com.jobscheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per HTTP request.
 *
 * HOW IT WORKS:
 * ─────────────
 * Every request to a protected endpoint goes through this filter.
 *
 * 1. Read "Authorization" header from the request
 *    Expected format: "Bearer eyJhbGciOiJIUzI1NiJ9..."
 *
 * 2. Extract the token (strip "Bearer ")
 *
 * 3. Extract the username from the token's "sub" claim
 *
 * 4. Load the user from MySQL via UserDetailsService
 *
 * 5. Validate the token (signature + expiry + username match)
 *
 * 6. If valid — set authentication in SecurityContextHolder
 *    This tells Spring Security: "this request is authenticated as username"
 *
 * 7. If invalid or missing — do nothing (SecurityContext stays empty)
 *    Spring Security will then reject the request with 401
 *
 * WHY OncePerRequestFilter?
 * Spring guarantees this filter runs exactly ONCE per request,
 * even if the request is forwarded internally.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // ── Step 1: Read Authorization header ────────────────────────────
        final String authHeader = request.getHeader("Authorization");

        // If no Authorization header, or doesn't start with "Bearer " → skip
        // Spring Security will handle the unauthenticated request downstream
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract token (remove "Bearer " prefix) ───────────────
        final String jwt = authHeader.substring(7);

        // ── Step 3: Extract username from token ───────────────────────────
        final String username;
        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (Exception e) {
            log.warn("[JWT-FILTER] Failed to extract username from token: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 4: Only process if username found AND not already auth'd ──
        // SecurityContextHolder.getContext().getAuthentication() being null means
        // this request hasn't been authenticated yet in this filter chain
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // ── Step 5: Load user from DB ─────────────────────────────────
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // ── Step 6: Validate token ────────────────────────────────────
            if (jwtUtil.isTokenValid(jwt, userDetails)) {

                // ── Step 7: Set authentication in SecurityContext ──────────
                // UsernamePasswordAuthenticationToken is Spring's standard
                // authentication object. null credentials = we trust the JWT.
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                           // credentials (not needed — JWT is the proof)
                                userDetails.getAuthorities()    // ROLE_ADMIN or ROLE_USER
                        );

                // Attach request details (IP address, session ID) to auth object
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set in SecurityContext — from this point, Spring treats request as authenticated
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("[JWT-FILTER] Authenticated user: {} path: {}",
                        username, request.getRequestURI());
            } else {
                log.warn("[JWT-FILTER] Invalid token for user: {}", username);
            }
        }

        // Continue with the next filter in the chain
        filterChain.doFilter(request, response);
    }
}
