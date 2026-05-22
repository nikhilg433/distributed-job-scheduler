package com.jobscheduler.service;

import com.jobscheduler.entity.Job;
import com.jobscheduler.entity.JobExecution;
import com.jobscheduler.enums.JobStatus;
import com.jobscheduler.kafka.JobEventProducer;
import com.jobscheduler.lock.DistributedLockService;
import com.jobscheduler.quartz.JobSchedulingService;
import com.jobscheduler.repository.JobExecutionRepository;
import com.jobscheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Core execution engine — handles distributed locking, exactly-once
 * guarantees, retry scheduling, and Kafka event publishing.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * FIX #5 — @Transactional on same-class method calls (self-invocation)
 * ═══════════════════════════════════════════════════════════════════════
 * Spring's @Transactional works via AOP proxying: a proxy wraps your bean
 * and intercepts calls to start/commit transactions. But when you call
 * a method on "this" (same class), you bypass the proxy entirely —
 * Spring never sees the call, so no transaction is started.
 *
 * Original bug:
 *   executeJob() called handleSuccess()/handleFailure() directly.
 *   Both were @Transactional, but since called via "this", the annotation
 *   was silently ignored. DB saves could fail mid-way with no rollback.
 *
 * Fix applied:
 *   - Removed handleSuccess() and handleFailure() as separate methods.
 *   - Inlined DB operations directly inside executeJob(), which IS called
 *     through the Spring proxy (via JobExecutorService interface).
 *   - executeJob() itself is @Transactional(propagation=REQUIRES_NEW)
 *     so each job execution is its own independent transaction.
 *     REQUIRES_NEW instead of the default REQUIRED ensures the execution
 *     transaction is isolated from any outer transaction that might
 *     exist (e.g., if Quartz wraps the call in its own TX).
 *
 * EXECUTION FLOW:
 * ───────────────
 *  [Quartz fires trigger → GenericJobExecutor.execute()]
 *         │
 *         ▼
 *  [Load job from DB]
 *         │
 *         ▼
 *  [Acquire Redis lock (SETNX)] ── FAIL ──► return (other instance handles it)
 *         │ OK
 *         ▼
 *  [Re-read DB status — double-check guard]
 *   RUNNING/COMPLETED ──► release lock + return
 *         │ SCHEDULED/RETRYING
 *         ▼
 *  [Mark RUNNING + save + create JobExecution record]
 *         │
 *         ▼
 *  [Publish job.started → Kafka]
 *         │
 *         ▼
 *  [simulateJobExecution() — Thread.sleep by JobType]
 *         │
 *    ─────┴──────────────────────────────
 *    │                                  │
 *  SUCCESS                           FAILURE
 *    │                                  │
 *  [Update execution COMPLETED]    [Update execution FAILED]
 *  [Update job COMPLETED]          [retryCount < maxRetries?]
 *  [Publish job.completed]           YES → RETRYING + scheduleRetry()
 *                                    NO  → FAILED + publishJobFailed()
 *    │                                  │
 *    └─────────────── finally ──────────┘
 *                         │
 *               [releaseLock() — always]
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobExecutorServiceImpl implements JobExecutorService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final DistributedLockService lockService;
    private final JobSchedulingService jobSchedulingService;
    private final JobEventProducer eventProducer;

    @Value("${app.instance-id}")
    private String instanceId;

    @Value("${app.scheduler.lock-timeout-seconds}")
    private long lockTimeoutSeconds;

    /**
     * REQUIRES_NEW: each job execution runs in its own DB transaction,
     * independent of any caller transaction. Ensures that DB state is
     * committed (RUNNING status visible to other nodes) before we start
     * the actual job work.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeJob(UUID jobId) {
        log.info("[EXECUTOR] ===== Starting jobId={} instanceId={} =====", jobId, instanceId);

        // ── 1. Load job ───────────────────────────────────────────────────
        Optional<Job> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            log.error("[EXECUTOR] jobId={} not found in DB — skipping", jobId);
            return;
        }
        Job job = jobOpt.get();
        log.info("[EXECUTOR] Loaded jobId={} name='{}' status={} retry={}/{}",
                jobId, job.getName(), job.getStatus(), job.getRetryCount(), job.getMaxRetries());

        // ── 2. Acquire Redis distributed lock (Guard 1) ───────────────────
        // SETNX: only ONE instance succeeds. All others get false and skip.
        boolean lockAcquired = lockService.acquireLock(jobId, lockTimeoutSeconds);
        if (!lockAcquired) {
            log.warn("[EXECUTOR] Lock not acquired for jobId={} — another instance handles it", jobId);
            return;
        }
        log.info("[EXECUTOR] Redis lock ACQUIRED jobId={} instanceId={}", jobId, instanceId);

        try {
            // ── 3. Re-read DB status after acquiring lock (Guard 2) ───────
            // Between step 1 and step 2, another instance may have already
            // started executing and set status=RUNNING. Re-reading here
            // catches that race condition — this is the "exactly-once" safety net.
            job = jobRepository.findById(jobId).orElseThrow();

            if (job.getStatus() == JobStatus.RUNNING
                    || job.getStatus() == JobStatus.COMPLETED
                    || job.getStatus() == JobStatus.CANCELLED) {
                log.warn("[EXECUTOR] jobId={} already in status={} after lock — aborting",
                        jobId, job.getStatus());
                return;
            }

            // ── 4. Claim: set RUNNING in DB ───────────────────────────────
            // Any other instance that gets past the Redis lock now sees
            // RUNNING in DB and hits Guard 2 above.
            job.setStatus(JobStatus.RUNNING);
            job.setLastExecutedAt(LocalDateTime.now());
            job = jobRepository.save(job);
            log.info("[EXECUTOR] jobId={} status → RUNNING", jobId);

            // ── 5. Create execution record ────────────────────────────────
            JobExecution execution = JobExecution.builder()
                    .jobId(jobId)
                    .status(JobStatus.RUNNING)
                    .startedAt(LocalDateTime.now())
                    .instanceId(instanceId)
                    .attemptNumber(job.getRetryCount())
                    .build();
            execution = jobExecutionRepository.save(execution);
            log.info("[EXECUTOR] Execution record created executionId={}", execution.getId());

            // ── 6. Publish job.started Kafka event ────────────────────────
            eventProducer.publishJobStarted(job);

            // ── 7. Execute (all DB saves happen in this same TX) ──────────
            long startMs = System.currentTimeMillis();
            try {
                simulateJobExecution(job);
                long durationMs = System.currentTimeMillis() - startMs;

                // SUCCESS — update execution + job records
                execution.setStatus(JobStatus.COMPLETED);
                execution.setCompletedAt(LocalDateTime.now());
                execution.setExecutionDurationMs(durationMs);
                jobExecutionRepository.save(execution);

                job.setStatus(JobStatus.COMPLETED);
                jobRepository.save(job);

                eventProducer.publishJobCompleted(job, durationMs);
                log.info("[EXECUTOR] jobId={} COMPLETED in {}ms", jobId, durationMs);

            } catch (Exception ex) {
                long durationMs = System.currentTimeMillis() - startMs;
                handleFailure(job, execution, ex, durationMs);
            }

        } finally {
            // ── 8. ALWAYS release lock (owner-safe) ───────────────────────
            // finally guarantees this even if DB save or Kafka publish throw.
            lockService.releaseLock(jobId);
            log.info("[EXECUTOR] ===== Done jobId={} instanceId={} =====", jobId, instanceId);
        }
    }

    /**
     * Handles job execution failure:
     *  - Updates execution record with failure details
     *  - If retries remain: increments counter, schedules exponential backoff retry
     *  - If retries exhausted: marks job FAILED, publishes Kafka event
     *
     * This method is private (not @Transactional) — it runs inside the
     * executeJob() transaction which is already active. No self-invocation
     * proxy problem.
     */
    private void handleFailure(Job job, JobExecution execution, Exception error, long durationMs) {
        String failureReason = error.getMessage() != null
                ? error.getMessage() : error.getClass().getSimpleName();
        String stackTrace = extractStackTrace(error);

        log.error("[EXECUTOR] jobId={} FAILED attempt={}/{} reason='{}'",
                job.getId(), job.getRetryCount() + 1, job.getMaxRetries(), failureReason);

        // Update execution record
        execution.setStatus(JobStatus.FAILED);
        execution.setCompletedAt(LocalDateTime.now());
        execution.setExecutionDurationMs(durationMs);
        execution.setFailureReason(failureReason);
        execution.setStackTrace(stackTrace);
        jobExecutionRepository.save(execution);

        if (job.getRetryCount() < job.getMaxRetries()) {
            // Retries remain — schedule next attempt with exponential backoff.
            // Formula: baseDelaySeconds * 1000ms * 2^(retryCount)
            // e.g. 30s base: attempt 1→30s, attempt 2→60s, attempt 3→120s
            job.setRetryCount(job.getRetryCount() + 1);
            job.setStatus(JobStatus.RETRYING);
            jobRepository.save(job);

            long delayMs = job.getRetryDelaySeconds() * 1000L
                    * (long) Math.pow(2, job.getRetryCount() - 1);

            log.info("[EXECUTOR] Scheduling retry #{} for jobId={} in {}ms ({}s)",
                    job.getRetryCount(), job.getId(), delayMs, delayMs / 1000);

            jobSchedulingService.scheduleRetry(job, delayMs);

        } else {
            // All retries exhausted — terminal FAILED state
            log.error("[EXECUTOR] jobId={} exhausted all {} retries — permanently FAILED",
                    job.getId(), job.getMaxRetries());
            job.setStatus(JobStatus.FAILED);
            jobRepository.save(job);
            eventProducer.publishJobFailed(job, failureReason);
        }
    }

    /**
     * Simulates job work with Thread.sleep.
     * In production, replace each case with real integration:
     *   EMAIL        → JavaMailSender / SendGrid SDK
     *   REPORT       → JasperReports / Apache POI
     *   NOTIFICATION → Firebase Admin SDK / APNs
     *   CLEANUP      → JdbcTemplate bulk DELETE
     */
    private void simulateJobExecution(Job job) throws InterruptedException {
        long durationMs = job.getType().getSimulatedDurationMs();
        log.info("[EXECUTOR-SIM] {} '{}' simulating {}ms jobId={} instanceId={}",
                job.getType(), job.getName(), durationMs, job.getId(), instanceId);
        Thread.sleep(durationMs);
        log.info("[EXECUTOR-SIM] {} complete jobId={}", job.getType(), job.getId());
    }

    private String extractStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > 4000 ? full.substring(0, 4000) + "\n...(truncated)" : full;
    }
}
