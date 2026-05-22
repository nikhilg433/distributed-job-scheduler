package com.jobscheduler.enums;

/**
 * Kafka event types published on every job state transition.
 * Each event is published to its own Kafka topic for fine-grained consumers.
 *
 * Event flow mirrors the JobStatus transitions:
 *   job.scheduled → job.started → job.completed
 *                              └→ job.failed
 *   job.cancelled (can happen from SCHEDULED or RUNNING state)
 */
public enum JobEventType {

    /**
     * Published when a job is first created and successfully scheduled in Quartz.
     * Topic: job.scheduled
     */
    JOB_SCHEDULED,

    /**
     * Published when a service instance acquires the distributed lock
     * and begins executing the job.
     * Topic: job.started
     */
    JOB_STARTED,

    /**
     * Published when job execution finishes without any exceptions.
     * Topic: job.completed
     */
    JOB_COMPLETED,

    /**
     * Published when all retry attempts are exhausted and the job is
     * permanently marked as FAILED.
     * Topic: job.failed
     */
    JOB_FAILED,

    /**
     * Published when a user cancels the job via the REST API.
     * Topic: job.cancelled
     */
    JOB_CANCELLED,

    /**
     * Published when a job fails but will be retried with exponential backoff.
     * Informational — useful for monitoring dashboards.
     * Stored in the audit trail but not sent to a separate Kafka topic.
     */
    JOB_RETRYING
}
