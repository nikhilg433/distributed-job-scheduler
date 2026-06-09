package com.jobscheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/auth/login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login credentials")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Username", example = "admin", required = true)
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "admin123", required = true)
    private String password;
}
