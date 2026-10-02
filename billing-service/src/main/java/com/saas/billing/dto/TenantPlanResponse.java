package com.saas.billing.dto;

import com.saas.common.enums.PlanType;
import java.util.UUID;

public record TenantPlanResponse(UUID tenantId, String tenantName, String billingEmail, PlanType planType, String currency) {
}