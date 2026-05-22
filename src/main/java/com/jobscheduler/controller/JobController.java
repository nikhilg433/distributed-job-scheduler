package com.jobscheduler.controller;

import com.jobscheduler.dto.*;
import com.jobscheduler.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API controller for all job management operations.
 *
 * Design rules:
 *  - No business logic here — only input validation, service delegation, response mapping
 *  - All request/response types are DTOs (no entity exposure)
 *  - Full Swagger documentation on every endpoint
 *  - Logging of all incoming requests with parameters
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Job Management", description = "Create, monitor, and control distributed scheduled jobs")
public class JobController {

    private final JobService jobService;

    // ─────────────────────────────────────────────────────────────────────
    // POST /api/jobs — Create a new job
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Create and schedule a new job",
            description = "Creates a job and immediately schedules it in Quartz. " +
                    "Provide either cronExpression (for recurring) or scheduledAt (for one-time)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Job created and scheduled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (validation or business rule violation)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        log.info("[API] POST /api/jobs — name='{}' type={}", request.getName(), request.getType());
        JobResponse response = jobService.createJob(request);
        log.info("[API] Job created: jobId={}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/jobs/{jobId} — Get a single job
    // ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Get job details by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found"),
            @ApiResponse(responseCode = "404", description = "Job not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(
            @Parameter(description = "Job UUID", required = true)
            @PathVariable UUID jobId) {
        log.debug("[API] GET /api/jobs/{}", jobId);
        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/jobs — List all jobs (paginated, optional status filter)
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "List all jobs",
            description = "Returns a paginated list of all jobs. Optionally filter by status."
    )
    @ApiResponse(responseCode = "200", description = "List of jobs")
    @GetMapping
    public ResponseEntity<Page<JobResponse>> getAllJobs(
            @Parameter(description = "Filter by status: SCHEDULED, RUNNING, COMPLETED, FAILED, RETRYING, PAUSED, CANCELLED")
            @RequestParam(required = false) String status,

            @Parameter(description = "Page number (0-based)", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "Sort direction: asc or desc", example = "desc")
            @RequestParam(defaultValue = "desc") String direction) {

        log.debug("[API] GET /api/jobs — status={} page={} size={}", status, page, size);

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        return ResponseEntity.ok(jobService.getAllJobs(status, pageable));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUT /api/jobs/{jobId}/cancel
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Cancel a scheduled job",
            description = "Removes the job from Quartz and marks it as CANCELLED. " +
                    "Cannot cancel RUNNING, COMPLETED, FAILED, or already CANCELLED jobs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job cancelled"),
            @ApiResponse(responseCode = "400", description = "Job is in a non-cancellable state"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PutMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancelJob(
            @Parameter(description = "Job UUID", required = true)
            @PathVariable UUID jobId) {
        log.info("[API] PUT /api/jobs/{}/cancel", jobId);
        jobService.cancelJob(jobId);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUT /api/jobs/{jobId}/pause
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Pause a scheduled job",
            description = "Pauses the Quartz trigger so the job won't fire until resumed. " +
                    "Only works on SCHEDULED jobs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job paused"),
            @ApiResponse(responseCode = "400", description = "Job is not in SCHEDULED state"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PutMapping("/{jobId}/pause")
    public ResponseEntity<Void> pauseJob(
            @Parameter(description = "Job UUID", required = true)
            @PathVariable UUID jobId) {
        log.info("[API] PUT /api/jobs/{}/pause", jobId);
        jobService.pauseJob(jobId);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUT /api/jobs/{jobId}/resume
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Resume a paused job",
            description = "Reactivates a paused Quartz trigger. Only works on PAUSED jobs."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job resumed"),
            @ApiResponse(responseCode = "400", description = "Job is not in PAUSED state"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PutMapping("/{jobId}/resume")
    public ResponseEntity<Void> resumeJob(
            @Parameter(description = "Job UUID", required = true)
            @PathVariable UUID jobId) {
        log.info("[API] PUT /api/jobs/{}/resume", jobId);
        jobService.resumeJob(jobId);
        return ResponseEntity.ok().build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/jobs/{jobId}/history
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get job execution history",
            description = "Returns all execution attempts for the job, sorted by start time (oldest first). " +
                    "Includes success, failure, and retry attempt details."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Execution history retrieved"),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{jobId}/history")
    public ResponseEntity<List<JobExecutionResponse>> getJobHistory(
            @Parameter(description = "Job UUID", required = true)
            @PathVariable UUID jobId) {
        log.debug("[API] GET /api/jobs/{}/history", jobId);
        return ResponseEntity.ok(jobService.getJobHistory(jobId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/jobs/stats
    // ─────────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Get system-wide job statistics",
            description = "Returns aggregate counts for each job status. Useful for dashboards."
    )
    @ApiResponse(responseCode = "200", description = "Statistics computed successfully")
    @GetMapping("/stats")
    public ResponseEntity<JobStatsResponse> getStats() {
        log.debug("[API] GET /api/jobs/stats");
        return ResponseEntity.ok(jobService.getStats());
    }
}
