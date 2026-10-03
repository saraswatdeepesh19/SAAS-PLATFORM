package com.saas.billing.pricing;

import java.math.BigDecimal;

/** One immutable calculated charge, retained separately so invoice totals remain explainable. */
public record PricingLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
}