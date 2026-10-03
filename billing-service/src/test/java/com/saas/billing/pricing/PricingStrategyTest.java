package com.saas.billing.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingStrategyTest {
    /** Confirms free-tier billing excludes the included 100 usage minutes. */
    @Test
    void freeTierChargesOnlyMinutesAboveAllowance() {
        assertEquals(new BigDecimal("300.00"), new FreeTierPricingStrategy().calculate(200).total());
    }

    /** Confirms flat-rate billing adds overage to the recurring plan fee. */
    @Test
    void flatRateIncludesFeeAndOverage() {
        assertEquals(new BigDecimal("1599.00"), new FlatRatePricingStrategy().calculate(1300).total());
    }

    /** Confirms tiered pricing applies marginal rates to their respective usage bands. */
    @Test
    void tieredPricingUsesMarginalBands() {
        assertEquals(new BigDecimal("3750.00"), new TieredPricingStrategy().calculate(2500).total());
    }
}