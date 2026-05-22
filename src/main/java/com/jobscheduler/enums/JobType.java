package com.jobscheduler.enums;

/**
 * Supported job types with their simulated execution durations.
 * In a real system, each type would have a dedicated executor class.
 * Here we simulate work with Thread.sleep to demonstrate the scheduling mechanics.
 */
public enum JobType {

    /**
     * Simulates sending an email (e.g., via SMTP or SendGrid).
     * Execution time: ~500ms
     */
    EMAIL(500),

    /**
     * Simulates generating a report (e.g., PDF or Excel export).
     * Execution time: ~1000ms
     */
    REPORT(1000),

    /**
     * Simulates sending a push notification (e.g., via FCM/APNs).
     * Execution time: ~200ms
     */
    NOTIFICATION(200),

    /**
     * Simulates a data cleanup task (e.g., purging old records).
     * Execution time: ~800ms
     */
    CLEANUP(800);

    /**
     * How many milliseconds to sleep to simulate this job type's work.
     */
    private final long simulatedDurationMs;

    JobType(long simulatedDurationMs) {
        this.simulatedDurationMs = simulatedDurationMs;
    }

    public long getSimulatedDurationMs() {
        return simulatedDurationMs;
    }
}
