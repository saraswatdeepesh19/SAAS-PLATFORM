package com.saas.notification.messaging;

import com.saas.common.constants.Topics;
import com.saas.common.events.InvoiceGeneratedEvent;
import com.saas.notification.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceEventConsumer {
    private final NotificationService notificationService;

    public InvoiceEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Delegates each invoice event to notification delivery handling. */
    @KafkaListener(topics = Topics.INVOICE_EVENTS, groupId = "notification-group")
    public void onInvoiceGenerated(InvoiceGeneratedEvent event) {
        notificationService.notify(event);
    }
}