package com.saas.common.events;

import java.time.Instant;
import java.util.UUID;

public record SessionEndedEvent(
        UUID eventId,
        UUID tenantId,
        UUID deviceId,
        UUID sessionId,
        long durationSec,
        Instant startedAt,
        Instant endedAt) {
}