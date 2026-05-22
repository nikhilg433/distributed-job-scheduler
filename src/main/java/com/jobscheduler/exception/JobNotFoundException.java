package com.jobscheduler.exception;

import java.util.UUID;

/**
 * Thrown when a requested job ID does not exist in the database.
 * Maps to HTTP 404 Not Found in GlobalExceptionHandler.
 */
public class JobNotFoundException extends RuntimeException {

    private final UUID jobId;

    public JobNotFoundException(UUID jobId) {
        super("No job found with id: " + jobId);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
