package com.jobscheduler.lock;

import java.util.UUID;

/**
 * Contract for acquiring and releasing distributed locks.
 *
 * Implementations must guarantee:
 *  1. At most one caller holds a lock for a given jobId at any time.
 *  2. Locks expire automatically (TTL) to prevent deadlocks on crashes.
 *  3. Only the instance that acquired the lock can release it (owner check).
 */
public interface DistributedLockService {

    /**
     * Attempts to acquire an exclusive lock for the given job.
     *
     * @param jobId     The job to lock.
     * @param ttlSeconds Lock expiration time. If the instance crashes, the lock
     *                  auto-expires after this many seconds, allowing recovery.
     * @return true if the lock was acquired successfully; false if another
     *         instance already holds the lock.
     */
    boolean acquireLock(UUID jobId, long ttlSeconds);

    /**
     * Releases the lock for the given job, but ONLY if this instance owns it.
     *
     * This prevents a slow instance from accidentally releasing a lock that
     * was re-acquired by another instance after TTL expiry.
     *
     * @param jobId The job whose lock to release.
     */
    void releaseLock(UUID jobId);

    /**
     * Checks whether a lock currently exists for the given job.
     *
     * @param jobId The job to check.
     * @return true if a lock exists (some instance is executing the job).
     */
    boolean isLocked(UUID jobId);
}
