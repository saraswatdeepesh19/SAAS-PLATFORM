package com.saas.common.events;

import java.time.Instant;
import java.util.UUID;

public record UsageAggregatedEvent(
        UUID eventId,
        UUID tenantId,
        String period,
        long totalSeconds,
        long totalSessions,
        Instant occurredAt) {
}