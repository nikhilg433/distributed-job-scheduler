package com.jobscheduler.repository;

import com.jobscheduler.entity.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access layer for the job_executions table.
 * Each row represents one execution attempt (original + retries).
 */
@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, UUID> {

    /**
     * Returns all execution attempts for a given job, sorted chronologically.
     * Used by GET /api/jobs/{jobId}/history
     */
    List<JobExecution> findByJobIdOrderByStartedAtAsc(UUID jobId);

    /**
     * Count total executions for a job — useful for stats.
     */
    long countByJobId(UUID jobId);
}
