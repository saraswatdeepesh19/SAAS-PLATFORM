package com.saas.auth.dto;

import java.util.UUID;

public record TenantResponse(UUID tenantId, String tenantName, UUID adminUserId) {
}