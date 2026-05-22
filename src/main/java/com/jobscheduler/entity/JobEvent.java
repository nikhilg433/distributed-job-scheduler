package com.jobscheduler.entity;

import com.jobscheduler.enums.JobEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted record of every Kafka event published for a job.
 *
 * This table serves as the audit log. Every state transition publishes
 * a Kafka message AND writes a row here. Even if Kafka is down, the
 * in-DB event log gives full visibility into what happened.
 *
 * The Kafka consumer (@KafkaListener) writes rows here as events arrive.
 */
@Entity
@Table(name = "job_events", indexes = {
        @Index(name = "idx_event_job_id", columnList = "jobId"),
        @Index(name = "idx_event_type", columnList = "eventType"),
        @Index(name = "idx_event_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The job this event is associated with. */
    @Column(nullable = false)
    private UUID jobId;

    /** Type of lifecycle event. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobEventType eventType;

    /**
     * JSON payload carrying event-specific metadata.
     * For JOB_FAILED: includes failureReason.
     * For JOB_STARTED: includes instanceId.
     * For all events: includes jobName, jobType, timestamp.
     */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /**
     * The service instance that published this event.
     * Useful for debugging which node triggered what.
     */
    @Column(length = 100)
    private String instanceId;

    /** Auto-set on INSERT. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
