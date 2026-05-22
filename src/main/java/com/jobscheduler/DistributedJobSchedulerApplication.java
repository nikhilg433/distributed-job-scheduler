package com.jobscheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Entry point for the Distributed Job Scheduler.
 *
 * FIX #11: Removed @EnableScheduling — this project uses Quartz for
 * scheduling (not Spring's @Scheduled annotation). @EnableScheduling
 * starts an unnecessary thread pool and causes confusion.
 *
 * @SpringBootApplication enables:
 *   @ComponentScan      — discovers all beans in com.jobscheduler.*
 *   @EnableAutoConfiguration — wires Quartz, Redis, Kafka, JPA from yml
 *   @Configuration      — this class can also define @Bean methods
 */
@SpringBootApplication
@Slf4j
public class DistributedJobSchedulerApplication {

    @Value("${app.instance-id:local-dev-1}")
    private String instanceId;

    @Value("${server.port:8080}")
    private String serverPort;

    public static void main(String[] args) {
        SpringApplication.run(DistributedJobSchedulerApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║        DISTRIBUTED JOB SCHEDULER — READY                ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  Instance ID  : {}                           ", instanceId);
        log.info("║  Swagger UI   : http://localhost:{}/swagger-ui.html ", serverPort);
        log.info("║  API Base     : http://localhost:{}/api/jobs        ", serverPort);
        log.info("║  Health       : http://localhost:{}/actuator/health  ", serverPort);
        log.info("╚══════════════════════════════════════════════════════════╝");
    }
}
