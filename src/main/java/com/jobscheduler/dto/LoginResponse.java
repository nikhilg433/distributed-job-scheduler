package com.jobscheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Response body for POST /api/auth/login
 * Contains the JWT token to use in subsequent requests via:
 * Authorization: Bearer <token>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "JWT authentication token response")
public class LoginResponse {

    @Schema(description = "JWT Bearer token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Token type — always 'Bearer'", example = "Bearer")
    private String type;

    @Schema(description = "Authenticated username", example = "admin")
    private String username;

    @Schema(description = "User role", example = "ADMIN")
    private String role;

    @Schema(description = "Token expiry in milliseconds", example = "86400000")
    private long expiresIn;
}
