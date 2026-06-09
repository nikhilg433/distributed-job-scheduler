package com.jobscheduler.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI (Swagger) configuration.
 *
 * Adds JWT Bearer auth button to Swagger UI so you can:
 *   1. Call POST /api/auth/login to get a token
 *   2. Click "Authorize" in Swagger UI
 *   3. Paste the token — all subsequent calls include it automatically
 *
 * UI:   http://localhost:8080/swagger-ui.html
 * Spec: http://localhost:8080/api-docs
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI distributedJobSchedulerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Job Scheduler API")
                        .description("""
                                Production-grade distributed job scheduling system.
                                
                                **Authentication:**
                                1. Call `POST /api/auth/login` with `{"username":"admin","password":"admin123"}`
                                2. Copy the `token` from the response
                                3. Click **Authorize** (lock icon) → paste the token → click Authorize
                                4. All subsequent API calls will include the JWT automatically
                                
                                **Roles:**
                                - `ADMIN` — full access: create, cancel, pause, resume, view
                                - `USER`  — read-only: list, get, history, stats
                                
                                **Key Features:**
                                - Distributed locking via Redis SETNX — exactly-once execution
                                - Quartz clustered JDBC job store — survives node crashes
                                - Kafka event streaming across 5 topics
                                - Exponential backoff retry (delay = base × 2ⁿ)
                                
                                **Job States:** SCHEDULED → RUNNING → COMPLETED / FAILED / RETRYING / CANCELLED
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Nikhil Garigipati")
                                .url("https://github.com/nikhilg433/distributed-job-scheduler"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Instance 1 — Primary"),
                        new Server()
                                .url("http://localhost:8081")
                                .description("Instance 2 — Distributed demo")
                ))
                // ── JWT Security Scheme ───────────────────────────────────
                // Adds the "Authorize" lock button to Swagger UI.
                // Users paste their Bearer token once — all endpoints use it.
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token (without 'Bearer ' prefix)")));
    }
}
