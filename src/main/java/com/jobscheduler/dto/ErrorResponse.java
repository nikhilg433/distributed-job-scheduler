package com.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Standardized error response format for all API errors.
 *
 * Every error — validation, not found, conflict, server error — returns
 * this consistent structure so clients can reliably parse failures.
 *
 * Example:
 * {
 *   "timestamp": "2024-01-15T10:30:00",
 *   "status": 404,
 *   "error": "Job Not Found",
 *   "message": "No job found with id: 550e8400-e29b-41d4-a716-446655440000",
 *   "path": "/api/jobs/550e8400-e29b-41d4-a716-446655440000"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Standardized API error response")
public class ErrorResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp when the error occurred")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "404")
    private int status;

    @Schema(description = "Short error category", example = "Job Not Found")
    private String error;

    @Schema(description = "Detailed human-readable error message",
            example = "No job found with id: 550e8400-e29b-41d4-a716-446655440000")
    private String message;

    @Schema(description = "Request path that caused the error",
            example = "/api/jobs/550e8400-e29b-41d4-a716-446655440000")
    private String path;
}
