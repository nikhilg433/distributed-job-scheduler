package com.jobscheduler.quartz;

import com.jobscheduler.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Facade that wraps all Quartz Scheduler interactions.
 *
 * Responsibilities:
 *  - Schedule a job (one-time or cron)
 *  - Pause a job's trigger
 *  - Resume a paused trigger
 *  - Delete (unschedule) a job
 *
 * The Quartz Scheduler bean is auto-configured by Spring Boot from
 * application.yml (spring.quartz.*). We inject it via @RequiredArgsConstructor.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobSchedulingService {

    private final Scheduler quartzScheduler;

    /**
     * Quartz group name for all our jobs.
     * Groups are used for bulk operations (pause all, resume all).
     */
    private static final String JOB_GROUP = "DEFAULT";

    // ─────────────────────────────────────────────────────────────────────
    // scheduleJob — creates a JobDetail + Trigger and registers with Quartz
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers a job with Quartz for future execution.
     *
     * HOW IT WORKS:
     * 1. Build a JobDetail that points to GenericJobExecutor.class
     *    and stores the jobId in a JobDataMap.
     * 2. Build a Trigger — either CronTrigger or SimpleTrigger.
     * 3. Register both with the Quartz Scheduler.
     * 4. Quartz persists them to QRTZ_JOB_DETAILS and QRTZ_TRIGGERS tables.
     * 5. On trigger fire time, Quartz calls GenericJobExecutor.execute().
     *
     * @param job The job entity to schedule
     * @return The Quartz job key name (stored on the Job entity for later management)
     */
    public String scheduleJob(Job job) {
        String jobKeyName = job.getId().toString();

        try {
            // ── Build JobDetail ───────────────────────────────────────────
            // JobDetail = "blueprint" for the job
            // - withIdentity: unique key in Quartz's job store
            // - ofType: which class to instantiate when the trigger fires
            // - usingJobData: key-value pairs available inside GenericJobExecutor
            // - storeDurably: keep the job even if it has no active triggers
            JobDetail jobDetail = JobBuilder.newJob(GenericJobExecutor.class)
                    .withIdentity(jobKeyName, JOB_GROUP)
                    .usingJobData(GenericJobExecutor.JOB_ID_KEY, job.getId().toString())
                    .withDescription(job.getName())
                    .storeDurably(false)
                    .build();

            // ── Build Trigger ─────────────────────────────────────────────
            Trigger trigger = buildTrigger(job, jobKeyName);

            // ── Schedule with Quartz ──────────────────────────────────────
            // scheduleJob atomically persists JobDetail + Trigger to QRTZ_* tables.
            // In clustered mode, all nodes see this immediately via the shared DB.
            Date firstFireTime = quartzScheduler.scheduleJob(jobDetail, trigger);
            log.info("[QUARTZ-SCHEDULE] jobId={} name='{}' firstFireTime={}",
                    job.getId(), job.getName(), firstFireTime);

            return jobKeyName;

        } catch (SchedulerException e) {
            log.error("[QUARTZ-SCHEDULE-ERROR] Failed to schedule jobId={}: {}", job.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to schedule job: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // pauseJob — pauses the trigger so it won't fire
    // ─────────────────────────────────────────────────────────────────────

    public void pauseJob(UUID jobId) {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), JOB_GROUP);
        try {
            quartzScheduler.pauseJob(jobKey);
            log.info("[QUARTZ-PAUSE] jobId={}", jobId);
        } catch (SchedulerException e) {
            log.error("[QUARTZ-PAUSE-ERROR] jobId={}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to pause job: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // resumeJob — resumes a paused trigger
    // ─────────────────────────────────────────────────────────────────────

    public void resumeJob(UUID jobId) {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), JOB_GROUP);
        try {
            quartzScheduler.resumeJob(jobKey);
            log.info("[QUARTZ-RESUME] jobId={}", jobId);
        } catch (SchedulerException e) {
            log.error("[QUARTZ-RESUME-ERROR] jobId={}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to resume job: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // deleteJob — removes the job and trigger from Quartz
    // ─────────────────────────────────────────────────────────────────────

    public void deleteJob(UUID jobId) {
        JobKey jobKey = JobKey.jobKey(jobId.toString(), JOB_GROUP);
        try {
            boolean deleted = quartzScheduler.deleteJob(jobKey);
            if (deleted) {
                log.info("[QUARTZ-DELETE] jobId={} successfully removed from scheduler", jobId);
            } else {
                log.warn("[QUARTZ-DELETE] jobId={} — job not found in Quartz (may have already fired)", jobId);
            }
        } catch (SchedulerException e) {
            log.error("[QUARTZ-DELETE-ERROR] jobId={}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete job from scheduler: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // scheduleRetry — reschedules a failed job with exponential backoff
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Schedules a retry execution after exponential backoff.
     *
     * Delay formula: baseDelay * 2^retryCount
     * Example with baseDelay=30s:
     *   Retry 1: 30 * 2^0 = 30  seconds
     *   Retry 2: 30 * 2^1 = 60  seconds
     *   Retry 3: 30 * 2^2 = 120 seconds
     *
     * @param job       The job to retry (must have updated retryCount)
     * @param delayMs   How many milliseconds to wait before the retry fires
     */
    public void scheduleRetry(Job job, long delayMs) {
        String jobKeyName = job.getId().toString() + "-retry-" + job.getRetryCount();

        try {
            // Compute the exact fire time
            Date retryFireTime = new Date(System.currentTimeMillis() + delayMs);

            JobDetail retryJobDetail = JobBuilder.newJob(GenericJobExecutor.class)
                    .withIdentity(jobKeyName, JOB_GROUP)
                    .usingJobData(GenericJobExecutor.JOB_ID_KEY, job.getId().toString())
                    .withDescription("Retry #" + job.getRetryCount() + " for " + job.getName())
                    .storeDurably(false)
                    .build();

            // SimpleTrigger fires exactly once at retryFireTime
            Trigger retryTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(jobKeyName + "-trigger", JOB_GROUP)
                    .startAt(retryFireTime)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow()) // Fire immediately if server was down
                    .forJob(retryJobDetail)
                    .build();

            quartzScheduler.scheduleJob(retryJobDetail, retryTrigger);
            log.info("[QUARTZ-RETRY-SCHEDULE] jobId={} retryCount={} delayMs={} fireAt={}",
                    job.getId(), job.getRetryCount(), delayMs, retryFireTime);

        } catch (SchedulerException e) {
            log.error("[QUARTZ-RETRY-ERROR] jobId={}: {}", job.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to schedule retry: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds either a CronTrigger or SimpleTrigger based on job configuration.
     */
    private Trigger buildTrigger(Job job, String jobKeyName) {
        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(jobKeyName + "-trigger", JOB_GROUP)
                .forJob(jobKeyName, JOB_GROUP);

        if (job.getCronExpression() != null && !job.getCronExpression().isBlank()) {
            // ── CronTrigger: recurring schedule ──────────────────────────
            // CronScheduleBuilder.cronSchedule() validates the expression.
            // withMisfireHandlingInstructionDoNothing → if the server was
            // down when the trigger should have fired, skip that firing
            // (don't try to catch up with missed executions).
            log.debug("[TRIGGER-BUILD] Building CronTrigger for jobId={} cron='{}'",
                    job.getId(), job.getCronExpression());
            return triggerBuilder
                    .withSchedule(CronScheduleBuilder
                            .cronSchedule(job.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
        } else {
            // ── SimpleTrigger: one-time execution ─────────────────────────
            // Convert LocalDateTime → Date for Quartz API compatibility
            Date fireDate = Date.from(
                    job.getScheduledAt().atZone(ZoneId.systemDefault()).toInstant()
            );
            log.debug("[TRIGGER-BUILD] Building SimpleTrigger for jobId={} fireAt='{}'",
                    job.getId(), fireDate);
            return triggerBuilder
                    .startAt(fireDate)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow())
                    .build();
        }
    }
}
