package com.saas.auth.dto;

import com.saas.common.enums.Role;
import java.util.UUID;

/** Public user representation that deliberately omits password hashes and account internals. */
public record UserResponse(UUID userId, UUID tenantId, String email, Role role) {
}