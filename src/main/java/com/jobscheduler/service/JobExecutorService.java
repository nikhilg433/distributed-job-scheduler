package com.jobscheduler.service;

import java.util.UUID;

/**
 * Service contract for the actual job execution logic.
 * Called by GenericJobExecutor when Quartz fires a trigger.
 * Handles: distributed locking, DB status checks, execution, retries, Kafka events.
 */
public interface JobExecutorService {

    /**
     * Executes a job with full distributed locking and exactly-once guarantees.
     *
     * Flow:
     * 1. Load job from DB
     * 2. Acquire Redis distributed lock (SETNX)
     * 3. Double-check DB status (guard against race conditions)
     * 4. Mark job as RUNNING
     * 5. Execute the job (simulate work based on JobType)
     * 6. Mark job as COMPLETED + release lock
     * 7. On failure: schedule retry with exponential backoff OR mark as FAILED
     * 8. Publish Kafka events at each state transition
     *
     * @param jobId UUID of the job to execute
     */
    void executeJob(UUID jobId);
}
