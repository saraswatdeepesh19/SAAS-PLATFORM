package com.saas.device.dto;

import java.time.Instant;
import java.util.UUID;

/** Immutable device view returned to clients with lifecycle state and optimistic version. */
public record DeviceResponse(
        UUID id, UUID tenantId, String name, String type, String os, String status, long version, Instant createdAt) {
}