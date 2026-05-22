package com.jobscheduler.enums;

/**
 * Represents the complete lifecycle of a scheduled job.
 *
 * State transition diagram:
 *
 *   SCHEDULED ──────────────────────────────► CANCELLED
 *       │
 *       │  (Quartz triggers execution)
 *       ▼
 *    RUNNING ─────────────────────────────── CANCELLED
 *       │                  │
 *       │ success          │ failure (retries remain)
 *       ▼                  ▼
 *   COMPLETED           RETRYING ──────────► RUNNING (re-attempt)
 *                          │
 *                          │ (max retries exhausted)
 *                          ▼
 *                        FAILED
 *
 *   PAUSED: Can be entered from SCHEDULED, exits back to SCHEDULED on resume.
 */
public enum JobStatus {

    /**
     * Job has been created and Quartz has scheduled it for future execution.
     * The job is waiting in the Quartz job store.
     */
    SCHEDULED,

    /**
     * A service instance has acquired the distributed lock and is actively
     * executing the job. No other instance should execute this job concurrently.
     */
    RUNNING,

    /**
     * Job finished successfully. Terminal state — no further transitions.
     */
    COMPLETED,

    /**
     * Job execution threw an exception. If retries remain, it transitions to
     * RETRYING. If retries are exhausted, it stays FAILED. Terminal state
     * when no retries are left.
     */
    FAILED,

    /**
     * Job failed but has remaining retry attempts. Quartz will reschedule it
     * with exponential backoff delay. Transitions back to RUNNING on next attempt.
     */
    RETRYING,

    /**
     * Quartz trigger has been paused. Job will not fire until resumed.
     * Can be resumed back to SCHEDULED.
     */
    PAUSED,

    /**
     * Job was explicitly cancelled by the user via REST API.
     * Terminal state — cannot be resumed once cancelled.
     */
    CANCELLED
}
