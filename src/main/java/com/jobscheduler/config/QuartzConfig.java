package com.jobscheduler.config;

import org.springframework.context.annotation.Configuration;

/**
 * Quartz Scheduler configuration.
 *
 * All Quartz properties are set in application.yml under spring.quartz.
 * The critical settings for distributed operation are:
 *
 *   isClustered: true
 *     → Quartz uses the QRTZ_LOCKS table in MySQL to coordinate across nodes.
 *       Only ONE node will fire each trigger at a time, preventing double execution
 *       at the Quartz level. Our Redis distributed lock adds a second layer of safety.
 *
 *   instanceId: AUTO
 *     → Each running JVM gets a unique Quartz scheduler instance ID.
 *       This is how Quartz tracks which node owns which trigger.
 *
 *   job-store-type: jdbc
 *     → Jobs are persisted in MySQL (not in-memory). If a node crashes and restarts,
 *       all scheduled jobs are recovered from the database automatically.
 *
 *   initialize-schema: always
 *     → On startup, Spring Boot runs the Quartz DDL script to create the QRTZ_*
 *       tables if they don't exist. Safe to leave as 'always' — it's idempotent.
 *
 * This class is intentionally minimal — Spring Boot's QuartzAutoConfiguration
 * handles the heavy lifting using properties from application.yml.
 * You only need this class if you want to customize SchedulerFactoryBean beans.
 */
@Configuration
public class QuartzConfig {
    // Spring Boot auto-configures Quartz from application.yml.
    // No additional beans required for basic clustered operation.
}
