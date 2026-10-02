package com.saas.auth.dto;

import com.saas.common.enums.Role;
import java.util.UUID;

public record UserResponse(UUID userId, UUID tenantId, String email, Role role) {
}