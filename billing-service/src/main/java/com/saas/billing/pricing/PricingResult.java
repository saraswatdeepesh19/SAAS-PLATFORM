package com.saas.billing.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PricingResult(List<PricingLine> lines, BigDecimal total) {
}