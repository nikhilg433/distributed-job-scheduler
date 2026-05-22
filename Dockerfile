# ─────────────────────────────────────────────
# Stage 1: Build the application with Maven
# ─────────────────────────────────────────────
FROM maven:3.9.6-amazoncorretto-17 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─────────────────────────────────────────────
# Stage 2: Run the application
# ─────────────────────────────────────────────
FROM amazoncorretto:17-alpine
WORKDIR /app

# Add a non-root user for security
RUN addgroup -S scheduler && adduser -S scheduler -G scheduler
USER scheduler

COPY --from=builder /app/target/distributed-job-scheduler-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
