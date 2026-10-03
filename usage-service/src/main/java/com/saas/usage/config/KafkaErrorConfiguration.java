package com.saas.usage.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorConfiguration {
    /** Retries transient Kafka handler failures and routes exhausted or invalid events to a dead-letter topic. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, exception) -> new TopicPartition(record.topic() + ".DLT", 0));
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(7000L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(InvalidUsageEventException.class);
        return errorHandler;
    }

    /** Marks malformed usage input as non-retryable so it can be sent directly to the dead-letter topic. */
    public static class InvalidUsageEventException extends RuntimeException {
        /** Carries a validation failure that should not be retried because the event itself is invalid. */
        public InvalidUsageEventException(String message) {
            super(message);
        }
    }
}