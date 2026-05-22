package com.jobscheduler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions.
 *
 * FIX #8: Removed the JsonMessageConverter bean. That bean told Spring Kafka
 * to use JSON conversion for @KafkaListener methods, but our consumer uses
 * StringDeserializer and parses JSON manually. Adding JsonMessageConverter
 * caused a type mismatch: Spring tried to convert the String message into a
 * typed object and threw a MessageConversionException.
 *
 * The correct setup is:
 *   Producer  → serialises Map to JSON String via ObjectMapper (in code)
 *               → sends with StringSerializer
 *   Consumer  → receives raw String
 *               → parses JSON with ObjectMapper (in code)
 * No Spring-level converter needed.
 */
@Configuration
public class KafkaConfig {

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

    @Bean
    public NewTopic jobScheduledTopic() {
        return TopicBuilder.name(jobScheduledTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobStartedTopic() {
        return TopicBuilder.name(jobStartedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobCompletedTopic() {
        return TopicBuilder.name(jobCompletedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobFailedTopic() {
        return TopicBuilder.name(jobFailedTopic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic jobCancelledTopic() {
        return TopicBuilder.name(jobCancelledTopic).partitions(3).replicas(1).build();
    }
}
