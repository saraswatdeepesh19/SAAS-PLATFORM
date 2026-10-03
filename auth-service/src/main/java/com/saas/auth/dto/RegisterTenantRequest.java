package com.saas.auth.dto;

import com.saas.common.enums.PlanType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated registration input for a tenant and its first administrator. */
public record RegisterTenantRequest(
        @NotBlank @Size(max = 150) String tenantName,
        @NotBlank @Email @Size(max = 200) String adminEmail,
        @NotBlank @Size(min = 8, max = 72) String password,
        PlanType planType) {
}