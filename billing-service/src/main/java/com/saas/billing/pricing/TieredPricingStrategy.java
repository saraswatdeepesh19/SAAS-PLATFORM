package com.saas.billing.pricing;

import com.saas.common.enums.PlanType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TieredPricingStrategy implements PricingStrategy {
    @Override
    public PlanType supports() {
        return PlanType.TIERED;
    }

    @Override
    public PricingResult calculate(long usageMinutes) {
        List<PricingLine> lines = new ArrayList<>();
        addTier(lines, Math.min(usageMinutes, 500), new BigDecimal("2.00"), "Tier 1 usage (0-500 minutes)");
        addTier(lines, Math.min(Math.max(usageMinutes - 500, 0), 1500),
                new BigDecimal("1.50"), "Tier 2 usage (501-2,000 minutes)");
        addTier(lines, Math.max(usageMinutes - 2000, 0), new BigDecimal("1.00"), "Tier 3 usage (above 2,000 minutes)");
        BigDecimal total = lines.stream().map(PricingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return new PricingResult(List.copyOf(lines), total);
    }

    private void addTier(List<PricingLine> lines, long quantity, BigDecimal rate, String description) {
        BigDecimal amount = BigDecimal.valueOf(quantity).multiply(rate).setScale(2, RoundingMode.HALF_UP);
        lines.add(new PricingLine(description, BigDecimal.valueOf(quantity), rate, amount));
    }
}