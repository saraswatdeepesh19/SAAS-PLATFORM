package com.saas.usage.messaging;

import com.saas.common.constants.Topics;
import com.saas.common.events.UsageAggregatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class UsageAggregatedPublisher {
    private static final Logger logger = LoggerFactory.getLogger(UsageAggregatedPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public UsageAggregatedPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Publishes updated totals only after the usage transaction commits. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UsageAggregatedEvent event) {
        logger.info("Publishing usage aggregate eventId={} tenantId={} period={} sessions={}",
            event.eventId(), event.tenantId(), event.period(), event.totalSessions());
        kafkaTemplate.send(Topics.USAGE_AGGREGATED, event.tenantId().toString(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        logger.error("Failed to publish UsageAggregatedEvent {}", event.eventId(), failure);
                    } else {
                        logger.info("UsageAggregatedEvent published eventId={} tenantId={} partition={} offset={}",
                                event.eventId(), event.tenantId(), result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}