package com.jobscheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Spring context smoke test — verifies all beans wire without errors.
 *
 * Strategy: mock the external infrastructure clients (Redis, Kafka)
 * so no actual broker connections are attempted. The JPA layer uses
 * H2 in-memory database. Quartz uses in-memory job store.
 *
 * This test runs in < 10 seconds with zero external dependencies.
 */
@SpringBootTest
@TestPropertySource(properties = {
    // ── H2 in-memory database (MySQL-compatible mode) ──────────────────
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",

    // ── Quartz in-memory (no QRTZ_* tables needed) ─────────────────────
    "spring.quartz.job-store-type=memory",

    // ── Kafka — mocked via @MockBean below, just needs valid config ─────
    "spring.kafka.bootstrap-servers=localhost:9092",

    // ── Redis — mocked via @MockBean below ─────────────────────────────
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=",

    // ── App properties ─────────────────────────────────────────────────
    "app.instance-id=test-instance",
    "app.scheduler.lock-timeout-seconds=30",
    "app.scheduler.default-max-retries=3",
    "app.scheduler.base-retry-delay-seconds=30",
    "app.kafka.topics.job-scheduled=job.scheduled",
    "app.kafka.topics.job-started=job.started",
    "app.kafka.topics.job-completed=job.completed",
    "app.kafka.topics.job-failed=job.failed",
    "app.kafka.topics.job-cancelled=job.cancelled"
})
class DistributedJobSchedulerApplicationTests {

    // Mock Redis and Kafka so no real connections are attempted
    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("rawtypes")
    @MockBean
    KafkaTemplate kafkaTemplate;

    @Test
    void contextLoads() {
        // If we reach here, the entire Spring context assembled correctly:
        // all beans wired, all @Value fields resolved, no circular dependencies.
    }
}
