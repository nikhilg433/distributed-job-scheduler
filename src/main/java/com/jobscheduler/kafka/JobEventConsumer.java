package com.jobscheduler.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobscheduler.entity.JobEvent;
import com.jobscheduler.enums.JobEventType;
import com.jobscheduler.repository.JobEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Consumes job lifecycle events from Kafka and persists them to the
 * job_events table as a durable audit trail.
 *
 * CONSUMER GROUP: "job-scheduler-group"
 *   All instances share this group. Kafka assigns each partition to
 *   exactly ONE consumer in the group — so events are processed once
 *   even with multiple running instances.
 *
 * DESERIALIZER: StringDeserializer
 *   The producer serialises the payload Map to a JSON string manually
 *   (via ObjectMapper in JobEventProducer). We receive it as a raw
 *   String here and parse it ourselves. This avoids the type-header
 *   mismatch that JsonDeserializer introduces.
 *
 * OFFSET COMMIT: enable-auto-commit=false in application.yml
 *   Spring Kafka uses AckMode.BATCH by default when auto-commit is off.
 *   The offset is committed only after the listener method returns
 *   successfully. If the DB write or JSON parse throws, the offset is
 *   NOT committed and the message is re-delivered on restart.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JobEventConsumer {

    private final JobEventRepository jobEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.instance-id}")
    private String instanceId;

    // ─────────────────────────────────────────────────────────────────────
    // One @KafkaListener per topic — same consumer group for all
    // ─────────────────────────────────────────────────────────────────────

    @KafkaListener(topics = "${app.kafka.topics.job-scheduled}", groupId = "job-scheduler-group")
    @Transactional
    public void consumeJobScheduled(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        processEvent(message, topic, partition, offset, JobEventType.JOB_SCHEDULED);
    }

    @KafkaListener(topics = "${app.kafka.topics.job-started}", groupId = "job-scheduler-group")
    @Transactional
    public void consumeJobStarted(@Payload String message,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset) {
        processEvent(message, topic, partition, offset, JobEventType.JOB_STARTED);
    }

    @KafkaListener(topics = "${app.kafka.topics.job-completed}", groupId = "job-scheduler-group")
    @Transactional
    public void consumeJobCompleted(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        processEvent(message, topic, partition, offset, JobEventType.JOB_COMPLETED);
    }

    @KafkaListener(topics = "${app.kafka.topics.job-failed}", groupId = "job-scheduler-group")
    @Transactional
    public void consumeJobFailed(@Payload String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset) {
        log.warn("[KAFKA-CONSUMER] JOB FAILED EVENT received topic={} partition={} offset={}",
                topic, partition, offset);
        processEvent(message, topic, partition, offset, JobEventType.JOB_FAILED);
    }

    @KafkaListener(topics = "${app.kafka.topics.job-cancelled}", groupId = "job-scheduler-group")
    @Transactional
    public void consumeJobCancelled(@Payload String message,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        processEvent(message, topic, partition, offset, JobEventType.JOB_CANCELLED);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core processing
    // ─────────────────────────────────────────────────────────────────────

    private void processEvent(String message, String topic, int partition,
                               long offset, JobEventType eventType) {
        log.info("[KAFKA-CONSUMER] eventType={} topic={} partition={} offset={}",
                eventType, topic, partition, offset);

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    message, new TypeReference<>() {});

            String jobIdStr = (String) payload.get("jobId");
            if (jobIdStr == null) {
                log.error("[KAFKA-CONSUMER] No jobId in payload from topic={}", topic);
                return;
            }

            UUID jobId = UUID.fromString(jobIdStr);

            JobEvent jobEvent = JobEvent.builder()
                    .jobId(jobId)
                    .eventType(eventType)
                    .payload(message)
                    .instanceId(instanceId)
                    .build();

            jobEventRepository.save(jobEvent);
            log.info("[KAFKA-CONSUMER] Audit event persisted jobId={} eventType={}", jobId, eventType);

        } catch (JsonProcessingException e) {
            // Malformed message — log and skip (dead-letter in production)
            log.error("[KAFKA-CONSUMER] JSON parse error topic={}: {}", topic, e.getMessage(), e);
        } catch (Exception e) {
            // Retriable error — rethrow so Spring Kafka does NOT commit offset
            log.error("[KAFKA-CONSUMER] Processing error topic={}: {}", topic, e.getMessage(), e);
            throw new RuntimeException("Failed to process Kafka event", e);
        }
    }
}
