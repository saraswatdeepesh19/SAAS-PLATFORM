package com.saas.common.events;

import java.time.Instant;
import java.util.UUID;

/** Cross-service monthly usage totals consumed by billing after aggregation commits. */
public record UsageAggregatedEvent(
        UUID eventId,
        UUID tenantId,
        String period,
        long totalSeconds,
        long totalSessions,
        Instant occurredAt) {
}