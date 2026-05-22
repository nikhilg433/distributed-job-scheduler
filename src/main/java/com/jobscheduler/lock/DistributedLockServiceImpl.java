package com.jobscheduler.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  DISTRIBUTED LOCK IMPLEMENTATION USING REDIS SETNX
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  WHAT IS SETNX?
 *  ─────────────
 *  SETNX = "SET if Not eXists". It is an atomic Redis command that sets a
 *  key ONLY IF that key does not already exist in Redis.
 *
 *  Redis guarantees that SETNX is atomic — even if 100 threads from 100
 *  different servers call SETNX for the same key at the same nanosecond,
 *  only ONE will receive "true" (success). All others receive "false".
 *
 *  This is the foundation of our distributed mutual exclusion lock.
 *
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  INSTANCE-1              INSTANCE-2                     │
 *  │                                                         │
 *  │  SETNX job-lock:123 ──► Redis ◄── SETNX job-lock:123   │
 *  │                           │                             │
 *  │                     (atomic choice)                     │
 *  │                           │                             │
 *  │  ◄── true (WINS) ─────────┤                             │
 *  │                           └─── false (LOSES) ──►        │
 *  │                                                         │
 *  │  Instance-1 executes job. Instance-2 skips it.          │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  WHY DO WE NEED A TTL?
 *  ──────────────────────
 *  If Instance-1 acquires the lock and then crashes (OutOfMemoryError,
 *  network partition, power failure), it will NEVER release the lock.
 *  Without TTL, the key lives in Redis forever → all other instances
 *  see "lock exists" and never execute the job → DEADLOCK.
 *
 *  By setting a TTL (e.g., 300 seconds), Redis automatically deletes the
 *  key after 5 minutes. Another instance can then acquire the lock and
 *  execute the job. This is called "lock lease" or "lock expiry".
 *
 *  LOCK KEY STRUCTURE
 *  ───────────────────
 *  Key:   "job-lock:{jobId}"         e.g. "job-lock:550e8400-e29b-41d4-..."
 *  Value: "{instanceId}"             e.g. "instance-1"
 *
 *  The value stores WHO holds the lock. This lets us implement "safe release"
 *  — only the instance that acquired the lock can release it.
 *
 *  WHY STORE THE INSTANCE ID AS VALUE?
 *  ─────────────────────────────────────
 *  Scenario: Instance-1 holds lock. It takes so long that TTL expires.
 *  Instance-2 acquires the lock. Now Instance-1 finishes and tries to
 *  release the lock. WITHOUT owner check, Instance-1 would delete Instance-2's
 *  lock — catastrophic! WITH owner check, we compare the value ("instance-1"
 *  vs "instance-2") and Instance-1's release is rejected.
 *
 *  NOTE ON PRODUCTION USAGE:
 *  ──────────────────────────
 *  For production systems, a Lua script should be used to make the
 *  GET + compare + DEL operation atomic:
 *
 *    if redis.call("get", KEYS[1]) == ARGV[1] then
 *        return redis.call("del", KEYS[1])
 *    else
 *        return 0
 *    end
 *
 *  This implementation uses a simpler approach (check-then-delete) which
 *  has a tiny theoretical race window. For a portfolio project, this
 *  demonstrates the concept clearly. RedisTemplate.execute(script) with
 *  a LuaScript bean is the production-hardened approach.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DistributedLockServiceImpl implements DistributedLockService {

    // ─────────────────────────────────────────────────────────────────────
    // Dependencies
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Spring's RedisTemplate gives us typed access to Redis commands.
     * We configured String serializers in RedisConfig so keys/values
     * are human-readable strings (not binary JDK-serialized blobs).
     */
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * This instance's unique identifier. Set via environment variable
     * APP_INSTANCE_ID (e.g., "instance-1", "instance-2").
     * Used as the lock value so we know WHO holds each lock.
     */
    @Value("${app.instance-id}")
    private String instanceId;

    // ─────────────────────────────────────────────────────────────────────
    // Lock key prefix — all job locks follow this pattern
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Lock key format: "job-lock:{jobId}"
     *
     * Why this prefix? In Redis, all keys share one namespace. The prefix
     * namespaces our locks so they don't collide with other Redis keys
     * (e.g., session data, cache entries). Easy to find with:
     *   redis-cli KEYS "job-lock:*"
     */
    private static final String LOCK_KEY_PREFIX = "job-lock:";

    // ─────────────────────────────────────────────────────────────────────
    // acquireLock — the core SETNX operation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Attempts to acquire an exclusive distributed lock for a job.
     *
     * UNDER THE HOOD (what happens in Redis):
     *   SET job-lock:{jobId} {instanceId} NX PX {ttlMs}
     *
     *   NX  = Only set if Not eXists (SETNX semantics)
     *   PX  = expiry in milliseconds (prevents deadlock on crash)
     *
     * This single atomic command replaces what used to require
     * two separate SETNX + EXPIRE calls (which had a race window
     * between the two commands if the process crashed between them).
     *
     * Spring's RedisTemplate.opsForValue().setIfAbsent(key, value, ttl)
     * translates directly to SET ... NX PX in the Redis protocol.
     *
     * @param jobId      UUID of the job to lock
     * @param ttlSeconds Lock will auto-expire after this many seconds
     * @return true = this instance acquired the lock and should execute the job
     *         false = another instance holds the lock; skip execution
     */
    @Override
    public boolean acquireLock(UUID jobId, long ttlSeconds) {
        // ── Step 1: Build the lock key ───────────────────────────────────
        // Key: "job-lock:550e8400-e29b-41d4-a716-446655440000"
        String lockKey = buildLockKey(jobId);

        // ── Step 2: The lock value is our instance ID ────────────────────
        // This lets us verify ownership before releasing (safe-release pattern)
        String lockValue = instanceId;

        // ── Step 3: Execute SET key value NX PX {ttl} ───────────────────
        // setIfAbsent → SETNX: sets the key ONLY if it does NOT already exist.
        // Returns Boolean.TRUE  if key was set   (we got the lock)
        // Returns Boolean.FALSE if key existed   (someone else holds the lock)
        // Returns null           on Redis error   (treat as failure)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));

        // ── Step 4: Log the outcome with instance ID ─────────────────────
        // Always include jobId and instanceId in log lines for distributed tracing
        if (Boolean.TRUE.equals(acquired)) {
            log.info("[LOCK-ACQUIRED] jobId={} instanceId={} lockKey={} ttlSeconds={}",
                    jobId, instanceId, lockKey, ttlSeconds);
            return true;
        } else {
            // Another instance beat us to it — this is NORMAL and expected
            // in a distributed system. It means the other instance will handle it.
            log.warn("[LOCK-FAILED] jobId={} instanceId={} — lock already held by another instance",
                    jobId, instanceId);
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // releaseLock — owner-safe lock release
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Releases the lock for the given job — but ONLY if this instance owns it.
     *
     * WHY THE OWNERSHIP CHECK?
     * ────────────────────────
     * Scenario without ownership check (dangerous):
     *   T=0:   Instance-1 acquires lock (TTL=300s)
     *   T=300: TTL expires → Redis deletes the lock automatically
     *   T=301: Instance-2 acquires the lock (new execution)
     *   T=302: Instance-1 finally finishes and calls releaseLock()
     *          Without ownership check → DEL deletes Instance-2's lock!
     *          → Instance-3 can now also start running the job → DOUBLE EXECUTION!
     *
     * Scenario WITH ownership check (safe):
     *   T=302: Instance-1 reads lock value → "instance-2" ≠ "instance-1"
     *          → Instance-1 does NOT delete the key
     *          → Instance-2's lock remains intact ✓
     *
     * @param jobId UUID of the job whose lock to release
     */
    @Override
    public void releaseLock(UUID jobId) {
        String lockKey = buildLockKey(jobId);

        // ── Step 1: Read the current lock owner ──────────────────────────
        // Who currently holds this lock?
        String currentOwner = redisTemplate.opsForValue().get(lockKey);

        // ── Step 2: Guard — only release if WE own it ────────────────────
        if (currentOwner == null) {
            // Lock already expired or was never set — nothing to release
            log.warn("[LOCK-RELEASE-SKIP] jobId={} instanceId={} — lock not found (already expired or released)",
                    jobId, instanceId);
            return;
        }

        if (!instanceId.equals(currentOwner)) {
            // We do NOT own this lock — another instance acquired it
            // This is the safety valve described above — DO NOT delete!
            log.warn("[LOCK-RELEASE-DENIED] jobId={} instanceId={} — lock owned by '{}', not releasing",
                    jobId, instanceId, currentOwner);
            return;
        }

        // ── Step 3: We own it — delete the key to release the lock ───────
        // NOTE: In production, use a Lua script (GET+DEL atomically) to
        // eliminate the tiny race window between steps 1 and 3 above.
        Boolean deleted = redisTemplate.delete(lockKey);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("[LOCK-RELEASED] jobId={} instanceId={} lockKey={}",
                    jobId, instanceId, lockKey);
        } else {
            // The key disappeared between our GET and DEL calls (TTL expired in that gap)
            // This is safe — the lock is gone either way
            log.warn("[LOCK-RELEASE-EXPIRED] jobId={} instanceId={} — key expired between check and delete",
                    jobId, instanceId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // isLocked — read-only lock check
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Checks whether a lock exists for the given job.
     * Used for monitoring and debugging — does NOT acquire the lock.
     *
     * @param jobId UUID to check
     * @return true if a live lock key exists in Redis
     */
    @Override
    public boolean isLocked(UUID jobId) {
        String lockKey = buildLockKey(jobId);
        Boolean exists = redisTemplate.hasKey(lockKey);
        return Boolean.TRUE.equals(exists);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helper
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the Redis key for a job lock.
     * Format: "job-lock:{jobId}"
     * Example: "job-lock:550e8400-e29b-41d4-a716-446655440000"
     */
    private String buildLockKey(UUID jobId) {
        return LOCK_KEY_PREFIX + jobId.toString();
    }
}
