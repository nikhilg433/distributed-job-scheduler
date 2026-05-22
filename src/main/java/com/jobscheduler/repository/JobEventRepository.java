package com.jobscheduler.repository;

import com.jobscheduler.entity.JobEvent;
import com.jobscheduler.enums.JobEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for the job_events audit log table.
 * Events are written by the Kafka consumer as messages arrive.
 */
@Repository
public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    /**
     * Returns all audit events for a job, newest first.
     * Used internally for audit trail queries.
     */
    List<JobEvent> findByJobIdOrderByCreatedAtDesc(UUID jobId);

    /**
     * Returns events of a specific type for a job.
     * Used to check if a JOB_STARTED event already exists (idempotency check).
     */
    List<JobEvent> findByJobIdAndEventType(UUID jobId, JobEventType eventType);
}
