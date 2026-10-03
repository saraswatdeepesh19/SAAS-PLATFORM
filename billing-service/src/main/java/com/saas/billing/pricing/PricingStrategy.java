package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;

public interface PricingStrategy {
    /** Identifies the plan this strategy prices so the factory can select it. */
    PlanType supports();

    /** Calculates itemized charges from usage minutes for the strategy's plan. */
    PricingResult calculate(long usageMinutes);
}