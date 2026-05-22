package com.jobscheduler.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.entity.Job;
import com.jobscheduler.enums.JobEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// KafkaTemplate<String, String>: key=jobId string, value=JSON string
// Matches StringSerializer configured in application.yml for both key and value.

/**
 * Publishes job lifecycle events to Apache Kafka.
 *
 * Every state transition in a job's lifecycle triggers a Kafka message.
 * This enables:
 *  - Real-time monitoring dashboards (subscribe to job.started/completed)
 *  - Audit logging (consume all events and persist to DB)
 *  - Alerting (consume job.failed and send PagerDuty/Slack alerts)
 *  - Analytics pipelines (compute average execution times, failure rates)
 *
 * KAFKA PRODUCER PATTERN:
 *  - Key: jobId (string) → ensures all events for the same job go to the
 *    same Kafka partition (ordering guarantee per job)
 *  - Value: JSON payload with event metadata
 *  - Async send with CompletableFuture callback for success/failure logging
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.instance-id}")
    private String instanceId;

    @Value("${app.kafka.topics.job-scheduled}")
    private String jobScheduledTopic;

    @Value("${app.kafka.topics.job-started}")
    private String jobStartedTopic;

    @Value("${app.kafka.topics.job-completed}")
    private String jobCompletedTopic;

    @Value("${app.kafka.topics.job-failed}")
    private String jobFailedTopic;

    @Value("${app.kafka.topics.job-cancelled}")
    private String jobCancelledTopic;

    // ─────────────────────────────────────────────────────────────────────
    // Public publish methods — one per lifecycle event
    // ─────────────────────────────────────────────────────────────────────

    public void publishJobScheduled(Job job) {
        Map<String, Object> payload = buildBasePayload(job, JobEventType.JOB_SCHEDULED);
        sendEvent(jobScheduledTopic, job.getId(), payload);
    }

    public void publishJobStarted(Job job) {
        Map<String, Object> payload = buildBasePayload(job, JobEventType.JOB_STARTED);
        payload.put("instanceId", instanceId);
        sendEvent(jobStartedTopic, job.getId(), payload);
    }

    public void publishJobCompleted(Job job, long durationMs) {
        Map<String, Object> payload = buildBasePayload(job, JobEventType.JOB_COMPLETED);
        payload.put("instanceId", instanceId);
        payload.put("executionDurationMs", durationMs);
        sendEvent(jobCompletedTopic, job.getId(), payload);
    }

    public void publishJobFailed(Job job, String failureReason) {
        Map<String, Object> payload = buildBasePayload(job, JobEventType.JOB_FAILED);
        payload.put("instanceId", instanceId);
        payload.put("failureReason", failureReason);
        payload.put("totalRetries", job.getRetryCount());
        sendEvent(jobFailedTopic, job.getId(), payload);
    }

    public void publishJobCancelled(Job job) {
        Map<String, Object> payload = buildBasePayload(job, JobEventType.JOB_CANCELLED);
        sendEvent(jobCancelledTopic, job.getId(), payload);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core send logic
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sends a Kafka message asynchronously.
     *
     * WHY ASYNC?
     * Synchronous Kafka sends would block the execution thread until
     * the broker acknowledges. For job lifecycle events, we don't want
     * Kafka availability to block job completion. We fire-and-forget,
     * logging failures for monitoring.
     *
     * @param topic   Target Kafka topic name
     * @param jobId   Used as the message key (ensures partition ordering per job)
     * @param payload Event data map to serialize as JSON
     */
    private void sendEvent(String topic, UUID jobId, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String messageKey = jobId.toString();

            // KafkaTemplate.send() returns a CompletableFuture<SendResult>
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, messageKey, payloadJson);

            // Attach callback for success/failure logging
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[KAFKA-SEND-FAILED] topic={} jobId={} error={}",
                            topic, jobId, ex.getMessage(), ex);
                } else {
                    log.debug("[KAFKA-SENT] topic={} jobId={} partition={} offset={}",
                            topic, jobId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

            log.info("[KAFKA-PUBLISH] eventType={} jobId={} topic={}",
                    payload.get("eventType"), jobId, topic);

        } catch (JsonProcessingException e) {
            // This should never happen for Map<String,Object> payloads
            log.error("[KAFKA-SERIALIZE-ERROR] jobId={} error={}", jobId, e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Payload builder
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the base event payload included in every event type.
     * Additional fields are added by specific publish methods above.
     */
    private Map<String, Object> buildBasePayload(Job job, JobEventType eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType.name());
        payload.put("jobId", job.getId().toString());
        payload.put("jobName", job.getName());
        payload.put("jobType", job.getType().name());
        payload.put("jobStatus", job.getStatus().name());
        payload.put("timestamp", LocalDateTime.now().toString());
        payload.put("publishedBy", instanceId);
        return payload;
    }
}
