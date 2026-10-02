package com.saas.common.events;

import com.saas.common.enums.PlanType;
import java.time.Instant;
import java.util.UUID;

public record TenantRegisteredEvent(
        UUID eventId,
        UUID tenantId,
        String tenantName,
        String adminEmail,
        PlanType planType,
        Instant occurredAt) {
}