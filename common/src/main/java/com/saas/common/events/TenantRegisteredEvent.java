package com.saas.common.events;

import com.saas.common.enums.PlanType;
import java.time.Instant;
import java.util.UUID;

/** Cross-service event describing a committed tenant registration and initial plan. */
public record TenantRegisteredEvent(
        UUID eventId,
        UUID tenantId,
        String tenantName,
        String adminEmail,
        PlanType planType,
        Instant occurredAt) {
}