package com.saas.device.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId, UUID deviceId, String status, Instant startedAt, Instant endedAt, Long durationSec) {
}