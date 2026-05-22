package com.jobscheduler.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jobscheduler.enums.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for GET /api/jobs/{jobId}/history
 * Represents a single execution attempt record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Details of a single job execution attempt")
public class JobExecutionResponse {

    @Schema(description = "Unique execution record ID")
    private UUID id;

    @Schema(description = "Parent job ID")
    private UUID jobId;

    @Schema(description = "Status of this specific execution attempt")
    private JobStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this attempt started")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "When this attempt finished (null if in progress)")
    private LocalDateTime completedAt;

    @Schema(description = "Failure reason if the attempt failed")
    private String failureReason;

    @Schema(description = "Java stack trace if the attempt threw an exception")
    private String stackTrace;

    @Schema(description = "Total execution duration in milliseconds")
    private Long executionDurationMs;

    @Schema(description = "Which service instance ran this attempt (e.g., instance-1)")
    private String instanceId;

    @Schema(description = "Attempt number (0=original, 1=first retry, etc.)")
    private int attemptNumber;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Record creation timestamp")
    private LocalDateTime createdAt;
}
