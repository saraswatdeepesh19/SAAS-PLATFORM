package com.saas.billing.messaging;

import com.saas.common.constants.Topics;
import com.saas.common.events.InvoiceGeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvoiceGeneratedPublisher {
    private static final Logger logger = LoggerFactory.getLogger(InvoiceGeneratedPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InvoiceGeneratedPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(InvoiceGeneratedEvent event) {
        kafkaTemplate.send(Topics.INVOICE_EVENTS, event.tenantId().toString(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        logger.error("Failed to publish InvoiceGeneratedEvent {}", event.eventId(), failure);
                        } else {
                            logger.info("InvoiceGeneratedEvent published eventId={} tenantId={} partition={} offset={}",
                                    event.eventId(), event.tenantId(), result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                    }
                });
    }
}