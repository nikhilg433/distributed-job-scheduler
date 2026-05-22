package com.jobscheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI (Swagger) configuration.
 * UI available at: http://localhost:8080/swagger-ui.html
 * JSON spec at:    http://localhost:8080/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI distributedJobSchedulerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Job Scheduler API")
                        .description("""
                                Production-grade distributed job scheduling system.
                                
                                **Key Features:**
                                - Schedule jobs via cron expression or one-time datetime
                                - Distributed locking via Redis SETNX — exactly-once execution
                                - Automatic retry with exponential backoff
                                - Job lifecycle events streamed to Apache Kafka
                                - Full execution history per job
                                
                                **Job States:** SCHEDULED → RUNNING → COMPLETED / FAILED / RETRYING / CANCELLED
                                
                                **Job Types:** EMAIL, REPORT, NOTIFICATION, CLEANUP
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Job Scheduler Team")
                                .email("scheduler@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server"),
                        new Server()
                                .url("http://localhost:8081")
                                .description("Local Development Server — Instance 2")
                ));
    }
}
