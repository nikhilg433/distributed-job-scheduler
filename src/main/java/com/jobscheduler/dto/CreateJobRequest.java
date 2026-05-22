package com.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobscheduler.enums.JobType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Request body for POST /api/jobs
 *
 * Either cronExpression or scheduledAt must be provided (validated in service layer).
 * Both cannot be present simultaneously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create and schedule a new job")
public class CreateJobRequest {

    @NotBlank(message = "Job name is required")
    @Size(min = 1, max = 255, message = "Job name must be between 1 and 255 characters")
    @Schema(description = "Human-readable job name", example = "Weekly Sales Report", required = true)
    private String name;

    @NotNull(message = "Job type is required")
    @Schema(description = "Type of job to execute", example = "REPORT", required = true,
            allowableValues = {"EMAIL", "REPORT", "NOTIFICATION", "CLEANUP"})
    private JobType type;

    @Schema(description = "Quartz cron expression for recurring jobs. Mutually exclusive with scheduledAt.",
            example = "0 0 9 * * ?")
    private String cronExpression;

    @Future(message = "scheduledAt must be a future datetime")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "One-time execution datetime (ISO-8601). Mutually exclusive with cronExpression.",
            example = "2025-12-31T23:59:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "Arbitrary JSON payload passed to the job executor",
            example = "{\"to\": \"user@example.com\", \"subject\": \"Report Ready\"}")
    private String payload;

    @Min(value = 0, message = "maxRetries must be 0 or greater")
    @Max(value = 10, message = "maxRetries cannot exceed 10")
    @Schema(description = "Maximum retry attempts on failure", example = "3", defaultValue = "3")
    @Builder.Default
    private int maxRetries = 3;

    @Min(value = 10, message = "retryDelaySeconds must be at least 10 seconds")
    @Schema(description = "Base retry delay in seconds (actual delay = base * 2^retryCount)",
            example = "30", defaultValue = "30")
    @Builder.Default
    private long retryDelaySeconds = 30L;

    @Min(value = 1, message = "priority must be between 1 and 10")
    @Max(value = 10, message = "priority must be between 1 and 10")
    @Schema(description = "Execution priority (1=lowest, 10=highest)", example = "5", defaultValue = "5")
    @Builder.Default
    private int priority = 5;
}
