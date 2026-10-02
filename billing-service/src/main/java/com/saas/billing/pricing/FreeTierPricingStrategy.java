package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FreeTierPricingStrategy implements PricingStrategy {
    private static final BigDecimal RATE = new BigDecimal("3.00");

    @Override
    public PlanType supports() {
        return PlanType.FREE_TIER;
    }

    @Override
    public PricingResult calculate(long usageMinutes) {
        long billableMinutes = Math.max(0, usageMinutes - 100);
        BigDecimal quantity = BigDecimal.valueOf(billableMinutes);
        BigDecimal amount = quantity.multiply(RATE).setScale(2, RoundingMode.HALF_UP);
        return new PricingResult(List.of(new PricingLine("Usage above 100 free minutes", quantity, RATE, amount)), amount);
    }
}