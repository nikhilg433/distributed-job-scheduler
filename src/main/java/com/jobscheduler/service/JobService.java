package com.jobscheduler.service;

import com.jobscheduler.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Primary service contract for all job management operations.
 * Controllers call only these methods — no direct repository access in controllers.
 */
public interface JobService {

    /**
     * Creates a new job, persists it, and schedules it in Quartz.
     *
     * @param request Validated job creation request
     * @return The created job's full details
     */
    JobResponse createJob(CreateJobRequest request);

    /**
     * Returns full details for a single job by ID.
     *
     * @throws com.jobscheduler.exception.JobNotFoundException if not found
     */
    JobResponse getJob(UUID jobId);

    /**
     * Returns a paginated list of all jobs, optionally filtered by status.
     *
     * @param status   Optional status filter (null = return all statuses)
     * @param pageable Pagination parameters (page, size, sort)
     */
    Page<JobResponse> getAllJobs(String status, Pageable pageable);

    /**
     * Cancels a scheduled job — removes it from Quartz and marks as CANCELLED.
     * Cannot cancel a job that is already RUNNING, COMPLETED, or FAILED.
     */
    void cancelJob(UUID jobId);

    /**
     * Pauses a job's Quartz trigger so it won't fire until resumed.
     * Only valid for SCHEDULED jobs.
     */
    void pauseJob(UUID jobId);

    /**
     * Resumes a paused job — reactivates the Quartz trigger.
     * Only valid for PAUSED jobs.
     */
    void resumeJob(UUID jobId);

    /**
     * Returns the full execution history for a job (all attempts).
     */
    java.util.List<JobExecutionResponse> getJobHistory(UUID jobId);

    /**
     * Returns aggregate statistics across all jobs.
     */
    JobStatsResponse getStats();
}
