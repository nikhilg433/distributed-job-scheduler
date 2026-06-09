package com.jobscheduler.controller;

import com.jobscheduler.dto.LoginRequest;
import com.jobscheduler.dto.LoginResponse;
import com.jobscheduler.security.JwtUtil;
import com.jobscheduler.security.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller — handles login and JWT token issuance.
 *
 * Flow:
 *   POST /api/auth/login with {username, password}
 *     → AuthenticationManager verifies credentials against MySQL
 *     → JwtUtil generates a signed JWT token
 *     → Returns token in LoginResponse
 *
 *   Client then sends:
 *     Authorization: Bearer <token>  on every subsequent request
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Login and JWT token management")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtil jwtUtil;

    @Operation(
            summary = "Login and get JWT token",
            description = """
                    Authenticate with username and password.
                    Returns a JWT Bearer token valid for 24 hours.
                    
                    **Default credentials (seeded on startup):**
                    - Admin: `admin` / `admin123` — full access
                    - User:  `user`  / `user123`  — read-only
                    
                    **Using the token:**
                    Add header to all requests: `Authorization: Bearer <token>`
                    
                    Or click the **Authorize** button in Swagger UI and paste the token.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful — JWT token returned"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password"),
            @ApiResponse(responseCode = "400", description = "Missing username or password")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("[AUTH] Login attempt for username: {}", request.getUsername());

        try {
            // Authenticate — throws BadCredentialsException if wrong password
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // Load full user details (including role)
            UserDetails userDetails = userDetailsService
                    .loadUserByUsername(request.getUsername());

            // Generate JWT token
            String token = jwtUtil.generateToken(userDetails);

            String role = userDetails.getAuthorities().stream()
                    .findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .orElse("USER");

            log.info("[AUTH] Login successful for username: {} role: {}",
                    request.getUsername(), role);

            return ResponseEntity.ok(LoginResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .username(request.getUsername())
                    .role(role)
                    .expiresIn(jwtUtil.getExpirationMs())
                    .build());

        } catch (BadCredentialsException e) {
            log.warn("[AUTH] Login failed for username: {} — bad credentials",
                    request.getUsername());
            return ResponseEntity.status(401).build();
        }
    }
}
