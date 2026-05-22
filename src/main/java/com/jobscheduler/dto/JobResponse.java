package com.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobscheduler.enums.JobStatus;
import com.jobscheduler.enums.JobType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for job read operations.
 * Never exposes the raw Job entity — always maps through this DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Full details and current status of a scheduled job")
public class JobResponse {

    @Schema(description = "Unique job identifier (UUID)")
    private UUID id;

    @Schema(description = "Human-readable job name")
    private String name;

    @Schema(description = "Job type")
    private JobType type;

    @Schema(description = "Current lifecycle status")
    private JobStatus status;

    @Schema(description = "Quartz cron expression (if recurring)")
    private String cronExpression;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "One-time execution datetime (if not recurring)")
    private LocalDateTime scheduledAt;

    @Schema(description = "JSON payload passed to the executor")
    private String payload;

    @Schema(description = "Maximum retry attempts configured")
    private int maxRetries;

    @Schema(description = "Current retry attempt count")
    private int retryCount;

    @Schema(description = "Base retry delay in seconds")
    private long retryDelaySeconds;

    @Schema(description = "Job priority (1=lowest, 10=highest)")
    private int priority;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp of the most recent execution attempt")
    private LocalDateTime lastExecutedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Job creation timestamp")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;
}
