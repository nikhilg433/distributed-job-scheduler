# Contributing

## Development Setup

```bash
# 1. Fork and clone
git clone https://github.com/YOUR_USERNAME/distributed-job-scheduler.git
cd distributed-job-scheduler

# 2. Start infrastructure
docker-compose up mysql redis zookeeper kafka -d

# 3. Run the application
./mvnw spring-boot:run

# 4. Verify — open Swagger UI
open http://localhost:8080/swagger-ui.html
```

## Branch Strategy

```
main        — stable, tagged releases only
develop     — integration branch
feature/*   — new features (branch from develop)
fix/*       — bug fixes (branch from develop)
```

## Commit Convention (Conventional Commits)

```
feat: add job priority queue
fix: release lock on executor crash
docs: update API curl examples
refactor: extract retry logic to RetryPolicy class
test: add distributed lock unit tests
chore: bump Spring Boot to 3.2.6
```

## Pull Request Checklist

- [ ] Code compiles: `mvn clean package -DskipTests`
- [ ] Tests pass: `mvn test`
- [ ] No new TODOs or commented-out code
- [ ] Swagger docs updated if API changed
- [ ] README updated if setup steps changed
