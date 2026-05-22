package com.jobscheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Response body for GET /api/jobs/stats
 * Provides a dashboard-level summary of all jobs in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Aggregate statistics across all jobs in the system")
public class JobStatsResponse {

    @Schema(description = "Total number of jobs ever created")
    private long totalJobs;

    @Schema(description = "Jobs currently waiting to be executed")
    private long scheduledJobs;

    @Schema(description = "Jobs actively being executed right now")
    private long runningJobs;

    @Schema(description = "Jobs that completed successfully")
    private long completedJobs;

    @Schema(description = "Jobs that exhausted all retries and permanently failed")
    private long failedJobs;

    @Schema(description = "Jobs waiting for their next retry attempt")
    private long retryingJobs;

    @Schema(description = "Jobs that were manually cancelled")
    private long cancelledJobs;

    @Schema(description = "Jobs whose Quartz trigger is paused")
    private long pausedJobs;
}
