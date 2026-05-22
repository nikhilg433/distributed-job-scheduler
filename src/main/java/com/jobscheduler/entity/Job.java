package com.jobscheduler.entity;

import com.jobscheduler.enums.JobStatus;
import com.jobscheduler.enums.JobType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core entity representing a scheduled job and its full configuration.
 *
 * Stored in the "jobs" table. Each row is a job definition including
 * its schedule, retry policy, current status, and execution metadata.
 */
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_job_status", columnList = "status"),
        @Index(name = "idx_job_type", columnList = "type"),
        @Index(name = "idx_job_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "executions")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** Human-readable label for the job (e.g., "Weekly Sales Report") */
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Job category — determines simulated execution behavior.
     * Stored as a string (not ordinal) for readability in DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobType type;

    /**
     * Current lifecycle state of the job.
     * Transitions: SCHEDULED → RUNNING → COMPLETED / FAILED / RETRYING / CANCELLED
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    /**
     * Quartz cron expression for recurring jobs.
     * Example: "0 0 9 * * ?" = every day at 9:00 AM
     * Mutually exclusive with scheduledAt.
     */
    @Column(length = 100)
    private String cronExpression;

    /**
     * Exact datetime for one-time job execution.
     * Mutually exclusive with cronExpression.
     */
    @Column
    private LocalDateTime scheduledAt;

    /**
     * Arbitrary JSON payload passed to the job executor.
     * Example: {"to": "user@example.com", "subject": "Report Ready"}
     */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Maximum number of retry attempts before marking as permanently FAILED. */
    @Column(nullable = false)
    @Builder.Default
    private int maxRetries = 3;

    /** How many retries have been attempted so far. */
    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /**
     * Base delay (seconds) for exponential backoff.
     * Actual delay for attempt N = retryDelay * 2^retryCount
     * Default: 30 seconds → 30s, 60s, 120s for retries 1, 2, 3
     */
    @Column(nullable = false)
    @Builder.Default
    private long retryDelaySeconds = 30L;

    /**
     * Higher priority jobs are executed first when multiple jobs are
     * ready simultaneously. Range: 1 (lowest) to 10 (highest).
     */
    @Column(nullable = false)
    @Builder.Default
    private int priority = 5;

    /** Timestamp of the most recent execution attempt (success or failure). */
    @Column
    private LocalDateTime lastExecutedAt;

    /** Auto-set on INSERT. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Auto-updated on every MERGE/UPDATE. */
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────────────
    // Quartz job key components — stored so we can delete/pause triggers
    // ─────────────────────────────────────────────────────────────────────

    /** Quartz job key name (= job ID string). */
    @Column(length = 100)
    private String quartzJobKey;

    /** Quartz job group (= "DEFAULT"). */
    @Column(length = 100)
    private String quartzJobGroup;
}
