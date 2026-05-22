# Distributed Job Scheduler

[![CI](https://github.com/YOUR_USERNAME/distributed-job-scheduler/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/distributed-job-scheduler/actions)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A **production-grade distributed job scheduling system** built with Java 17 and Spring Boot 3.  
Jobs run **exactly once** across multiple service instances via Redis SETNX distributed locking, Quartz clustered JDBC job store, and Apache Kafka lifecycle events.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                     DISTRIBUTED JOB SCHEDULER                        │
│                                                                      │
│   REST Client          Instance 1 (:8080)    Instance 2 (:8081)     │
│       │                     │                      │                 │
│       └────── HTTP ─────────┤                      │                 │
│                             │                      │                 │
│              ┌──────────────┼──────────────────────┤                 │
│              │              │                      │                 │
│              ▼              ▼                      ▼                 │
│        ┌──────────┐  ┌──────────┐          ┌──────────┐            │
│        │  MySQL 8 │  │  Redis 7 │          │  Kafka   │            │
│        │          │  │          │          │          │            │
│        │ jobs     │  │SETNX Lock│          │job.sched │            │
│        │ executions  │job-lock: │          │job.start │            │
│        │ events   │  │{jobId}   │          │job.done  │            │
│        │ QRTZ_*   │  │ TTL=300s │          │job.fail  │            │
│        └──────────┘  └──────────┘          └──────────┘            │
└──────────────────────────────────────────────────────────────────────┘
```

## How Exactly-Once Execution Works

```
Quartz trigger fires on both instances
           │
    ┌──────┴──────┐
    │             │
Instance-1    Instance-2
    │             │
[SETNX] ✅   [SETNX] ❌ → skip, return
    │
[Re-read DB] status=SCHEDULED ✅
    │
[Set RUNNING in DB]
    │
[Execute job]
    │
[Set COMPLETED + release lock]
```

**Two independent guards prevent double execution:**
1. **Redis SETNX** — atomic; only one instance wins the lock
2. **DB status re-check** — catches edge case where lock TTL expires mid-execution

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Scheduler | Quartz 2.x (clustered JDBC) |
| Distributed Lock | Redis 7 — SETNX+TTL, implemented manually with `RedisTemplate` |
| Event Streaming | Apache Kafka (5 topics, JSON payloads) |
| Database | MySQL 8 |
| ORM | Spring Data JPA + Hibernate |
| API Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Containerisation | Docker + Docker Compose |
| CI | GitHub Actions |
| Build | Maven 3.9+ |

---

## Prerequisites

- **Docker Desktop 4.x+** and **Docker Compose 2.x+**
- Java 17+ and Maven 3.9+ *(only needed for local dev without Docker)*

---

## Quick Start

```bash
git clone https://github.com/YOUR_USERNAME/distributed-job-scheduler.git
cd distributed-job-scheduler

# Build image and start all services
docker-compose up --build -d

# Tail logs from both app instances
docker-compose logs -f app-instance-1 app-instance-2
```

Wait for:
```
DISTRIBUTED JOB SCHEDULER — READY
```

| Service | URL |
|---------|-----|
| App Instance 1 | http://localhost:8080 |
| App Instance 2 | http://localhost:8081 |
| **Swagger UI** | **http://localhost:8080/swagger-ui.html** |
| Kafka UI | http://localhost:8090 |
| Health check | http://localhost:8080/actuator/health |

### Local Development (without Docker)

```bash
# Start only infrastructure
docker-compose up mysql redis zookeeper kafka -d

# Run the app
./mvnw spring-boot:run
```

---

## API Reference

### Create a one-time job
```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Welcome Email",
    "type": "EMAIL",
    "scheduledAt": "2025-12-31T10:00:00",
    "payload": "{\"to\":\"user@example.com\"}",
    "maxRetries": 3,
    "retryDelaySeconds": 30,
    "priority": 8
  }' | jq .
```

### Create a recurring cron job
```bash
curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Daily Sales Report",
    "type": "REPORT",
    "cronExpression": "0 0 9 * * ?",
    "payload": "{\"format\":\"PDF\"}",
    "maxRetries": 2
  }' | jq .
```

### Get job details
```bash
curl -s http://localhost:8080/api/jobs/{jobId} | jq .
```

### List all jobs (paginated)
```bash
curl -s "http://localhost:8080/api/jobs?page=0&size=10&sortBy=createdAt&direction=desc" | jq .

# Filter by status
curl -s "http://localhost:8080/api/jobs?status=RUNNING" | jq .
```

### Cancel / Pause / Resume
```bash
curl -s -X PUT http://localhost:8080/api/jobs/{jobId}/cancel
curl -s -X PUT http://localhost:8080/api/jobs/{jobId}/pause
curl -s -X PUT http://localhost:8080/api/jobs/{jobId}/resume
```

### Execution history
```bash
curl -s http://localhost:8080/api/jobs/{jobId}/history | jq .
```

### System stats
```bash
curl -s http://localhost:8080/api/jobs/stats | jq .
# → { "totalJobs":42, "runningJobs":3, "completedJobs":18, ... }
```

---

## Job Types & States

**Types:** `EMAIL` (500ms) · `REPORT` (1000ms) · `NOTIFICATION` (200ms) · `CLEANUP` (800ms)

**State machine:**
```
SCHEDULED ──► RUNNING ──► COMPLETED
    │              └──► RETRYING ──► RUNNING (retry loop)
    │                       └──► FAILED  (retries exhausted)
    └──────────────────────────► CANCELLED (manual)
    └── PAUSED ──► SCHEDULED (resumed)
```

---

## Error Response Format

All API errors return a consistent JSON body:

```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 404,
  "error": "Job Not Found",
  "message": "No job found with id: 550e8400-e29b-41d4-a716-446655440000",
  "path": "/api/jobs/550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Database Schema

**`jobs`** — job definitions and current state  
**`job_executions`** — one row per execution attempt (supports retry history)  
**`job_events`** — Kafka event audit trail, written by the consumer  
**`QRTZ_*`** — 11 Quartz internal tables (auto-created on startup)

---

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| Redis SETNX (manual) over Redisson | Demonstrates understanding of the primitive; no opaque library |
| `SET key value NX EX ttl` (single command) | Atomic; old two-step SETNX+EXPIRE had a crash window between commands |
| Lock value = instance ID | Enables safe release — only the owner can delete the lock |
| `@Transactional(REQUIRES_NEW)` on executeJob | Each execution is isolated; RUNNING status is committed immediately so other nodes see it |
| DB status re-check after lock acquisition | Second guard for edge case where Redis TTL expires mid-execution |
| Quartz JDBC clustered store | Jobs survive server restart; Quartz itself ensures one-node-per-trigger via `SELECT FOR UPDATE` on `QRTZ_LOCKS` |
| StringSerializer/Deserializer for Kafka | Avoids type-header mismatch with JsonDeserializer; payload is serialised manually with ObjectMapper |

---

## Concepts Used

| Concept | Explanation |
|---------|-------------|
| **SETNX** | SET if Not eXists — atomic Redis primitive for mutual exclusion |
| **Lock TTL** | Auto-expiry prevents deadlock when a node crashes while holding the lock |
| **Exactly-once execution** | Redis lock + DB status double-check across two independent failure domains |
| **Quartz clustering** | `isClustered=true` + JDBC store; `SELECT FOR UPDATE` on `QRTZ_LOCKS` elects one node per trigger |
| **Exponential backoff** | Retry delay = `baseDelay * 2^retryCount` — reduces thundering herd on failure |
| **Kafka key = jobId** | All events for one job land on the same partition — ordering guaranteed per job |
| **Consumer group** | Multiple instances share partitions; each message processed by exactly one instance |
| **@Transactional REQUIRES_NEW** | Starts a fresh transaction regardless of caller; status committed before execution begins |
| **Defense in depth** | Two independent guards (Redis + DB) for the critical exactly-once requirement |
| **DTO pattern** | Decouples API contract from DB schema; entities never exposed directly |
| **GlobalExceptionHandler** | `@RestControllerAdvice` centralises all error mapping; controllers stay clean |

---

## Stopping

```bash
docker-compose down          # Stop containers
docker-compose down -v       # Stop + delete volumes (clean slate)
```

---

## License

MIT — see [LICENSE](LICENSE)
