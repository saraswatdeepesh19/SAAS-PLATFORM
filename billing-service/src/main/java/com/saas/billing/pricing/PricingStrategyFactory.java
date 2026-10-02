package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PricingStrategyFactory {
    private final Map<PlanType, PricingStrategy> strategies;

    public PricingStrategyFactory(List<PricingStrategy> strategies) {
        this.strategies = new EnumMap<>(PlanType.class);
        strategies.forEach(strategy -> this.strategies.put(strategy.supports(), strategy));
    }

    public PricingStrategy forPlan(PlanType planType) {
        PricingStrategy strategy = strategies.get(planType);
        if (strategy == null) {
            throw new IllegalArgumentException("No pricing strategy for plan " + planType);
        }
        return strategy;
    }
}