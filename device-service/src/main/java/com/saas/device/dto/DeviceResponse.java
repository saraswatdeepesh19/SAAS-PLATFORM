package com.saas.device.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id, UUID tenantId, String name, String type, String os, String status, long version, Instant createdAt) {
}