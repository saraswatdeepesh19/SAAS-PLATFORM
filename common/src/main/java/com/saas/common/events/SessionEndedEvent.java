package com.saas.common.events;

import java.time.Instant;
import java.util.UUID;

/** Cross-service event carrying the completed session data required for usage aggregation. */
public record SessionEndedEvent(
        UUID eventId,
        UUID tenantId,
        UUID deviceId,
        UUID sessionId,
        long durationSec,
        Instant startedAt,
        Instant endedAt) {
}