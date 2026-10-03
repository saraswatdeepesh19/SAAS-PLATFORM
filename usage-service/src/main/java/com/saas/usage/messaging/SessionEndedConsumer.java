package com.saas.usage.messaging;

import com.saas.common.constants.Topics;
import com.saas.common.events.SessionEndedEvent;
import com.saas.usage.service.UsageProcessingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SessionEndedConsumer {
    private final UsageProcessingService processingService;

    public SessionEndedConsumer(UsageProcessingService processingService) {
        this.processingService = processingService;
    }

    /** Delegates completed-session events to the transactional usage processor. */
    @KafkaListener(topics = Topics.USAGE_EVENTS, groupId = "usage-group")
    public void onMessage(SessionEndedEvent event) {
        processingService.process(event);
    }
}