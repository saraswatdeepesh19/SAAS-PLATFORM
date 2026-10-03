package com.saas.auth.dto;

import java.util.UUID;

/** Returns the newly created tenant and initial administrator identifiers without internal entity data. */
public record TenantResponse(UUID tenantId, String tenantName, UUID adminUserId) {
}