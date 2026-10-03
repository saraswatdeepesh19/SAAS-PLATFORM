package com.saas.auth.messaging;

import com.saas.common.constants.Topics;
import com.saas.common.events.TenantRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TenantRegisteredKafkaPublisher {
    private static final Logger logger = LoggerFactory.getLogger(TenantRegisteredKafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TenantRegisteredKafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes tenant registration only after its database transaction commits.
     * This prevents consumers from acting on tenant data that was later rolled back.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TenantRegisteredEvent event) {
        try {
            kafkaTemplate.send(Topics.TENANT_EVENTS, event.tenantId().toString(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            logger.error("Failed to publish TenantRegisteredEvent {}", event.eventId(), failure);
                        } else {
                            logger.info("TenantRegisteredEvent published eventId={} tenantId={} partition={} offset={}",
                                    event.eventId(), event.tenantId(), result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (RuntimeException exception) {
            logger.error("Failed to publish TenantRegisteredEvent {}", event.eventId(), exception);
        }
    }
}