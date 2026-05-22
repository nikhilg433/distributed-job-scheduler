package com.jobscheduler.exception;

import java.util.UUID;

/**
 * Thrown when an attempt is made to execute a job that is already running.
 * This is the double-check guard — if Redis lock is acquired but DB shows
 * RUNNING status, we abort to guarantee exactly-once execution.
 * Maps to HTTP 409 Conflict in GlobalExceptionHandler.
 */
public class JobAlreadyRunningException extends RuntimeException {

    private final UUID jobId;

    public JobAlreadyRunningException(UUID jobId) {
        super("Job is already running with id: " + jobId);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
