package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FlatRatePricingStrategy implements PricingStrategy {
    private static final BigDecimal MONTHLY_FEE = new BigDecimal("999.00");
    private static final BigDecimal OVERAGE_RATE = new BigDecimal("2.00");

    /** Returns the plan handled here so the factory can select this strategy. */
    @Override
    public PlanType supports() {
        return PlanType.FLAT_RATE;
    }

    /** Applies the monthly fee plus any overage beyond the included allowance. */
    @Override
    public PricingResult calculate(long usageMinutes) {
        long overageMinutes = Math.max(0, usageMinutes - 1000);
        BigDecimal overage = BigDecimal.valueOf(overageMinutes).multiply(OVERAGE_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        List<PricingLine> lines = List.of(
                new PricingLine("Monthly flat rate (includes 1,000 minutes)", BigDecimal.ONE, MONTHLY_FEE, MONTHLY_FEE),
                new PricingLine("Usage above 1,000 included minutes", BigDecimal.valueOf(overageMinutes),
                        OVERAGE_RATE, overage));
        return new PricingResult(lines, MONTHLY_FEE.add(overage).setScale(2, RoundingMode.HALF_UP));
    }
}