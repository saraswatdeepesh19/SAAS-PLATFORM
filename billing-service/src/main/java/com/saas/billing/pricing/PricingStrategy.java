package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;

public interface PricingStrategy {
    PlanType supports();

    PricingResult calculate(long usageMinutes);
}