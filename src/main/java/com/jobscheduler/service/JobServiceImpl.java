package com.jobscheduler.service;

import com.jobscheduler.dto.*;
import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.JobExecution;
import com.jobscheduler.enums.JobStatus;
import com.jobscheduler.exception.InvalidJobRequestException;
import com.jobscheduler.exception.JobNotFoundException;
import com.jobscheduler.kafka.JobEventProducer;
import com.jobscheduler.quartz.JobSchedulingService;
import com.jobscheduler.repository.JobExecutionRepository;
import com.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of all job management operations.
 *
 * Responsibilities:
 *  - Validate and create jobs
 *  - Delegate scheduling to Quartz via JobSchedulingService
 *  - Handle lifecycle operations (cancel, pause, resume)
 *  - Map entities ↔ DTOs
 *  - Publish Kafka events on state changes
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobSchedulingService jobSchedulingService;
    private final JobEventProducer eventProducer;

    // ─────────────────────────────────────────────────────────────────────
    // createJob
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public JobResponse createJob(CreateJobRequest request) {
        log.info("[JOB-CREATE] name='{}' type={} cron='{}' scheduledAt={}",
                request.getName(), request.getType(),
                request.getCronExpression(), request.getScheduledAt());

        // ── Business rule: must have exactly one of cron or scheduledAt ───
        validateScheduleInput(request);

        // ── Build and persist the job entity ─────────────────────────────
        Job job = Job.builder()
                .name(request.getName())
                .type(request.getType())
                .status(JobStatus.SCHEDULED)
                .cronExpression(request.getCronExpression())
                .scheduledAt(request.getScheduledAt())
                .payload(request.getPayload())
                .maxRetries(request.getMaxRetries())
                .retryDelaySeconds(request.getRetryDelaySeconds())
                .priority(request.getPriority())
                .build();

        job = jobRepository.save(job);
        log.info("[JOB-CREATE] Persisted jobId={}", job.getId());

        // ── Register with Quartz scheduler ────────────────────────────────
        String quartzKey = jobSchedulingService.scheduleJob(job);
        job.setQuartzJobKey(quartzKey);
        job.setQuartzJobGroup("DEFAULT");
        job = jobRepository.save(job);

        log.info("[JOB-CREATE] Scheduled in Quartz. jobId={} quartzKey={}", job.getId(), quartzKey);

        // ── Publish Kafka event ───────────────────────────────────────────
        eventProducer.publishJobScheduled(job);

        return mapToResponse(job);
    }

    // ─────────────────────────────────────────────────────────────────────
    // getJob
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJob(UUID jobId) {
        log.debug("[JOB-GET] jobId={}", jobId);
        Job job = findJobOrThrow(jobId);
        return mapToResponse(job);
    }

    // ─────────────────────────────────────────────────────────────────────
    // getAllJobs — paginated, optional status filter
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<JobResponse> getAllJobs(String status, Pageable pageable) {
        log.debug("[JOB-LIST] status={} page={} size={}",
                status, pageable.getPageNumber(), pageable.getPageSize());

        Page<Job> jobs;
        if (status != null && !status.isBlank()) {
            try {
                JobStatus jobStatus = JobStatus.valueOf(status.toUpperCase());
                jobs = jobRepository.findByStatus(jobStatus, pageable);
            } catch (IllegalArgumentException e) {
                throw new InvalidJobRequestException("Invalid status filter: " + status +
                        ". Valid values: SCHEDULED, RUNNING, COMPLETED, FAILED, RETRYING, PAUSED, CANCELLED");
            }
        } else {
            jobs = jobRepository.findAll(pageable);
        }

        log.debug("[JOB-LIST] Found {} jobs (total={})", jobs.getNumberOfElements(), jobs.getTotalElements());
        return jobs.map(this::mapToResponse);
    }

    // ─────────────────────────────────────────────────────────────────────
    // cancelJob
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void cancelJob(UUID jobId) {
        log.info("[JOB-CANCEL] jobId={}", jobId);
        Job job = findJobOrThrow(jobId);

        // ── Guard: can only cancel SCHEDULED, PAUSED, or RETRYING jobs ───
        if (job.getStatus() == JobStatus.RUNNING) {
            throw new InvalidJobRequestException(
                    "Cannot cancel a job that is currently RUNNING. jobId=" + jobId);
        }
        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED
                || job.getStatus() == JobStatus.CANCELLED) {
            throw new InvalidJobRequestException(
                    "Cannot cancel a job in terminal state: " + job.getStatus() + ". jobId=" + jobId);
        }

        // ── Remove from Quartz ────────────────────────────────────────────
        try {
            jobSchedulingService.deleteJob(jobId);
        } catch (Exception e) {
            // Log but don't fail — job may have already fired
            log.warn("[JOB-CANCEL] Could not delete from Quartz for jobId={}: {}", jobId, e.getMessage());
        }

        // ── Update DB status ──────────────────────────────────────────────
        job.setStatus(JobStatus.CANCELLED);
        jobRepository.save(job);

        // ── Publish Kafka event ───────────────────────────────────────────
        eventProducer.publishJobCancelled(job);

        log.info("[JOB-CANCEL] Successfully cancelled jobId={}", jobId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // pauseJob
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void pauseJob(UUID jobId) {
        log.info("[JOB-PAUSE] jobId={}", jobId);
        Job job = findJobOrThrow(jobId);

        if (job.getStatus() != JobStatus.SCHEDULED) {
            throw new InvalidJobRequestException(
                    "Can only pause SCHEDULED jobs. Current status: " + job.getStatus() + ". jobId=" + jobId);
        }

        jobSchedulingService.pauseJob(jobId);
        job.setStatus(JobStatus.PAUSED);
        jobRepository.save(job);

        log.info("[JOB-PAUSE] Successfully paused jobId={}", jobId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // resumeJob
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void resumeJob(UUID jobId) {
        log.info("[JOB-RESUME] jobId={}", jobId);
        Job job = findJobOrThrow(jobId);

        if (job.getStatus() != JobStatus.PAUSED) {
            throw new InvalidJobRequestException(
                    "Can only resume PAUSED jobs. Current status: " + job.getStatus() + ". jobId=" + jobId);
        }

        jobSchedulingService.resumeJob(jobId);
        job.setStatus(JobStatus.SCHEDULED);
        jobRepository.save(job);

        log.info("[JOB-RESUME] Successfully resumed jobId={}", jobId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // getJobHistory
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<JobExecutionResponse> getJobHistory(UUID jobId) {
        log.debug("[JOB-HISTORY] jobId={}", jobId);
        // Verify job exists first
        findJobOrThrow(jobId);

        List<JobExecution> executions = jobExecutionRepository
                .findByJobIdOrderByStartedAtAsc(jobId);

        log.debug("[JOB-HISTORY] Found {} execution records for jobId={}", executions.size(), jobId);
        return executions.stream()
                .map(this::mapExecutionToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // getStats
    // ─────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public JobStatsResponse getStats() {
        log.debug("[JOB-STATS] Computing aggregate statistics");
        return JobStatsResponse.builder()
                .totalJobs(jobRepository.count())
                .scheduledJobs(jobRepository.countByStatus(JobStatus.SCHEDULED))
                .runningJobs(jobRepository.countByStatus(JobStatus.RUNNING))
                .completedJobs(jobRepository.countByStatus(JobStatus.COMPLETED))
                .failedJobs(jobRepository.countByStatus(JobStatus.FAILED))
                .retryingJobs(jobRepository.countByStatus(JobStatus.RETRYING))
                .cancelledJobs(jobRepository.countByStatus(JobStatus.CANCELLED))
                .pausedJobs(jobRepository.countByStatus(JobStatus.PAUSED))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    private Job findJobOrThrow(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    private void validateScheduleInput(CreateJobRequest request) {
        boolean hasCron = request.getCronExpression() != null && !request.getCronExpression().isBlank();
        boolean hasDateTime = request.getScheduledAt() != null;

        if (!hasCron && !hasDateTime) {
            throw new InvalidJobRequestException(
                    "Either cronExpression or scheduledAt must be provided");
        }
        if (hasCron && hasDateTime) {
            throw new InvalidJobRequestException(
                    "Provide either cronExpression or scheduledAt, not both");
        }
    }

    private JobResponse mapToResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .name(job.getName())
                .type(job.getType())
                .status(job.getStatus())
                .cronExpression(job.getCronExpression())
                .scheduledAt(job.getScheduledAt())
                .payload(job.getPayload())
                .maxRetries(job.getMaxRetries())
                .retryCount(job.getRetryCount())
                .retryDelaySeconds(job.getRetryDelaySeconds())
                .priority(job.getPriority())
                .lastExecutedAt(job.getLastExecutedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private JobExecutionResponse mapExecutionToResponse(JobExecution execution) {
        return JobExecutionResponse.builder()
                .id(execution.getId())
                .jobId(execution.getJobId())
                .status(execution.getStatus())
                .startedAt(execution.getStartedAt())
                .completedAt(execution.getCompletedAt())
                .failureReason(execution.getFailureReason())
                .stackTrace(execution.getStackTrace())
                .executionDurationMs(execution.getExecutionDurationMs())
                .instanceId(execution.getInstanceId())
                .attemptNumber(execution.getAttemptNumber())
                .createdAt(execution.getCreatedAt())
                .build();
    }
}
