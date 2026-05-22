package com.jobscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for distributed locking.
 *
 * We configure a RedisTemplate<String, String> so lock keys and values
 * are stored as plain UTF-8 strings — making them easy to inspect in
 * redis-cli (e.g., "GET job-lock:550e8400-e29b-41d4-a716-446655440000").
 *
 * We do NOT use Redisson. Everything is implemented manually with SETNX
 * (SET if Not eXists) + TTL via RedisTemplate commands.
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate configured with String serializers for both
     * keys and values. Spring Boot auto-configures the connection factory
     * from application.yml (spring.data.redis.*).
     *
     * Why StringRedisSerializer?
     *  - Default JDK serialization produces binary garbage in redis-cli
     *  - String serialization keeps lock keys human-readable for debugging
     *  - Lock values (instance IDs) are plain strings anyway
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializers for both keys and values
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
