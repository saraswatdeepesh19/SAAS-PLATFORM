package com.saas.billing.pricing;

import java.math.BigDecimal;
import java.util.List;

/** Complete pricing output containing both auditable line items and their total. */
public record PricingResult(List<PricingLine> lines, BigDecimal total) {
}