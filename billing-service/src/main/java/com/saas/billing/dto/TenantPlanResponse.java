package com.saas.billing.dto;

import com.saas.common.enums.PlanType;
import java.util.UUID;

/** Tenant billing settings consumed by invoice generation and returned to billing clients. */
public record TenantPlanResponse(UUID tenantId, String tenantName, String billingEmail, PlanType planType, String currency) {
}