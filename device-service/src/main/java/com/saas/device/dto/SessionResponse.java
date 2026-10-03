package com.saas.device.dto;

import java.time.Instant;
import java.util.UUID;

/** Immutable session status and timing information returned to device clients. */
public record SessionResponse(
        UUID sessionId, UUID deviceId, String status, Instant startedAt, Instant endedAt, Long durationSec) {
}