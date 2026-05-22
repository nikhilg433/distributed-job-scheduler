package com.jobscheduler.quartz;

import com.jobscheduler.service.JobExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  HOW QUARTZ WORKS — A COMPLETE EXPLANATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  QUARTZ CORE CONCEPTS:
 *  ─────────────────────
 *
 *  1. Job (interface) — the unit of work
 *     Your class implements org.quartz.Job with one method: execute().
 *     Quartz instantiates a NEW instance of this class for EACH execution
 *     (it does NOT reuse the same object). This is why we use @Autowired
 *     and Spring's integration — Quartz asks Spring to create the bean
 *     (with DI), not Java's "new" keyword.
 *
 *  2. JobDetail — the job blueprint/configuration
 *     JobDetail stores: which Job class to use, a name+group key,
 *     and a JobDataMap (key-value store for parameters).
 *     It's like a class definition — you can have one JobDetail
 *     referenced by multiple Triggers.
 *
 *  3. Trigger — the schedule
 *     Two main types:
 *     - SimpleTrigger: fire once at a specific time (or every N seconds)
 *     - CronTrigger:   fire on a cron schedule ("every Monday at 9am")
 *
 *     One JobDetail can have MULTIPLE triggers if needed, but we use 1:1.
 *
 *  4. Scheduler — the Quartz engine
 *     Manages all JobDetails and Triggers. On startup, it loads all
 *     persisted jobs from the QRTZ_* database tables. At the right time,
 *     it fires the trigger → instantiates the Job class → calls execute().
 *
 *  HOW CLUSTERING WORKS:
 *  ─────────────────────
 *  With isClustered=true and JobStoreTX (JDBC-backed store):
 *
 *  1. All scheduler instances share the same MySQL QRTZ_* tables.
 *  2. When a trigger fires, Quartz uses SELECT ... FOR UPDATE on the
 *     QRTZ_LOCKS table to elect ONE node to execute it.
 *  3. The winning node marks the trigger as "acquired" and calls execute().
 *  4. Other nodes see the trigger as "acquired" and skip it.
 *
 *  This gives us Quartz-level single-execution. Our Redis lock adds a
 *  SECOND layer of safety (defense in depth).
 *
 *  HOW SPRING + QUARTZ WORK TOGETHER:
 *  ────────────────────────────────────
 *  Normally, Quartz creates Job objects with "new YourJobClass()".
 *  This means Spring @Autowired fields would be null (no DI!).
 *
 *  Spring Boot's QuartzAutoConfiguration installs a custom
 *  SpringBeanJobFactory that overrides job instantiation:
 *    → Instead of "new GenericJobExecutor()"
 *    → It calls applicationContext.getBean(GenericJobExecutor.class)
 *    → So Spring creates the instance with all DI resolved ✓
 *
 *  EXECUTION FLOW (step by step):
 *  ────────────────────────────────
 *  1. A job's trigger fires (time arrived or cron expression matched)
 *  2. Quartz's thread pool picks up the trigger
 *  3. Quartz creates a GenericJobExecutor instance (via Spring DI)
 *  4. Quartz calls execute(JobExecutionContext context)
 *  5. We extract the jobId from JobDataMap
 *  6. We delegate to JobExecutorService.executeJob(jobId)
 *  7. JobExecutorService acquires Redis lock → executes → releases lock
 *  8. Quartz marks the trigger as complete (or schedules retry for CronTrigger)
 *
 *  WHY ONE GENERIC EXECUTOR?
 *  ──────────────────────────
 *  Instead of separate QuartzEmailJob, QuartzReportJob, etc. — which would
 *  require registering multiple Job classes in Quartz — we use ONE generic
 *  executor that reads jobType from the JobDataMap and delegates.
 *  This keeps the Quartz integration thin and the business logic in services.
 */
@Component
@Slf4j
public class GenericJobExecutor implements Job {

    /**
     * The actual execution logic lives here.
     * GenericJobExecutor's only responsibility: extract the jobId
     * from Quartz context and hand off to the service layer.
     *
     * Why not inject via constructor? Quartz requires a no-args constructor.
     * @Autowired field injection is the standard workaround for Quartz Jobs.
     */
    @Autowired
    private JobExecutorService jobExecutorService;

    /**
     * JobDataMap key used to pass the job UUID from scheduling time to execution time.
     * The same constant is used when WRITING the key (in JobSchedulingService)
     * and READING it here, ensuring consistency.
     */
    public static final String JOB_ID_KEY = "jobId";

    /**
     * Called by Quartz when a trigger fires.
     *
     * The JobExecutionContext provides:
     *  - context.getJobDetail().getJobDataMap() → parameters set at scheduling time
     *  - context.getFireTime() → exact time the trigger fired
     *  - context.getScheduledFireTime() → when it was supposed to fire
     *  - context.getJobRunTime() → how long the job ran (available after execution)
     *
     * @param context Quartz execution context — do not store a reference to this
     *                beyond the scope of execute(), as Quartz reuses it.
     * @throws JobExecutionException If thrown, Quartz can optionally re-fire the job.
     *                               We handle retries manually, so we catch all exceptions
     *                               inside jobExecutorService.executeJob() and don't
     *                               propagate them here.
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // ── Step 1: Extract the JobDataMap ────────────────────────────────
        // JobDataMap is set at job scheduling time in JobSchedulingService.
        // It's persisted in the QRTZ_JOB_DETAILS table (serialized as a BLOB).
        JobDataMap dataMap = context.getMergedJobDataMap();

        // ── Step 2: Extract the job ID ────────────────────────────────────
        // We stored the job's UUID as a string in the data map.
        String jobIdString = dataMap.getString(JOB_ID_KEY);

        if (jobIdString == null || jobIdString.isBlank()) {
            log.error("[QUARTZ-EXECUTOR] No jobId found in JobDataMap. Trigger: {}",
                    context.getTrigger().getKey());
            // Throw JobExecutionException to signal Quartz that something is wrong
            throw new JobExecutionException("Missing jobId in JobDataMap");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdString);
        } catch (IllegalArgumentException e) {
            log.error("[QUARTZ-EXECUTOR] Invalid jobId format in JobDataMap: {}", jobIdString);
            throw new JobExecutionException("Invalid jobId format: " + jobIdString);
        }

        // ── Step 3: Log execution trigger ─────────────────────────────────
        log.info("[QUARTZ-EXECUTOR] Trigger fired for jobId={} at fireTime={} scheduledTime={}",
                jobId,
                context.getFireTime(),
                context.getScheduledFireTime());

        // ── Step 4: Delegate to the service layer ─────────────────────────
        // This is where distributed locking, DB status checks, actual
        // job execution, retry scheduling, and Kafka events all happen.
        // We do NOT throw here — exceptions are handled inside executeJob().
        try {
            jobExecutorService.executeJob(jobId);
        } catch (Exception e) {
            // executeJob() should not throw, but if it does (e.g., DB down),
            // log it and let Quartz know via JobExecutionException.
            // Setting refireImmediately=false — our retry logic is manual.
            log.error("[QUARTZ-EXECUTOR] Unexpected error executing jobId={}: {}",
                    jobId, e.getMessage(), e);
            throw new JobExecutionException(e, false);
        }

        log.info("[QUARTZ-EXECUTOR] Execution completed for jobId={}", jobId);
    }
}
