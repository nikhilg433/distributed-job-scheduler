package com.jobscheduler.entity;

import com.jobscheduler.enums.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records a single execution attempt of a job.
 *
 * One job can have many executions (initial attempt + retries).
 * This table provides the full audit trail of every time a job was run,
 * which instance ran it, how long it took, and why it failed (if it did).
 */
@Entity
@Table(name = "job_executions", indexes = {
        @Index(name = "idx_execution_job_id", columnList = "jobId"),
        @Index(name = "idx_execution_status", columnList = "status"),
        @Index(name = "idx_execution_started_at", columnList = "startedAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** FK to the parent job. Not a JPA relationship to avoid lazy-loading issues. */
    @Column(nullable = false)
    private UUID jobId;

    /** Status of this specific execution attempt. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobStatus status;

    /** When this execution attempt began. */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** When this execution attempt finished (null if still running or failed instantly). */
    @Column
    private LocalDateTime completedAt;

    /**
     * Human-readable description of what went wrong.
     * Example: "java.net.ConnectException: SMTP server unreachable"
     */
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    /**
     * Full Java stack trace of the exception, if any.
     * Stored as TEXT for post-mortem debugging.
     */
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    /** How long this execution took in milliseconds. Null if not yet completed. */
    @Column
    private Long executionDurationMs;

    /**
     * The hostname/instance ID of the service node that ran this job.
     * Format: "instance-1", "instance-2", or a UUID.
     * Critical for debugging which node executed the job.
     */
    @Column(length = 100)
    private String instanceId;

    /** Which retry attempt number this was (0 = original, 1 = first retry, etc.) */
    @Column(nullable = false)
    @Builder.Default
    private int attemptNumber = 0;

    /** Auto-set on INSERT. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
