package com.saas.billing.messaging;

import com.saas.billing.service.BillingEventService;
import com.saas.common.constants.Topics;
import com.saas.common.events.TenantRegisteredEvent;
import com.saas.common.events.UsageAggregatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BillingEventConsumer {
    private final BillingEventService billingEventService;

    public BillingEventConsumer(BillingEventService billingEventService) {
        this.billingEventService = billingEventService;
    }

    /** Hands tenant registration events to the billing service to initialize plan state. */
    @KafkaListener(topics = Topics.TENANT_EVENTS, groupId = "billing-group")
    public void onTenantRegistered(TenantRegisteredEvent event) {
        billingEventService.onTenantRegistered(event);
    }

    /** Hands usage totals to billing so monthly snapshots stay current for invoicing. */
    @KafkaListener(topics = Topics.USAGE_AGGREGATED, groupId = "billing-group")
    public void onUsageAggregated(UsageAggregatedEvent event) {
        billingEventService.onUsageAggregated(event);
    }
}