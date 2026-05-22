package com.jobscheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.quartz.job-store-type=memory",
    "spring.quartz.properties.org.quartz.jobStore.isClustered=false",
    "spring.quartz.properties.org.quartz.scheduler.instanceId=test-instance",
    "spring.quartz.properties.org.quartz.threadPool.threadCount=2",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password=",
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

    @MockBean(name = "redisTemplate")
    RedisTemplate<String, String> redisTemplate;

    @SuppressWarnings("rawtypes")
    @MockBean
    KafkaTemplate kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
