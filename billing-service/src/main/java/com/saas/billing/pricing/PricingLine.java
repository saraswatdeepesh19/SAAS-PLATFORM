package com.saas.billing.pricing;

import java.math.BigDecimal;

public record PricingLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
}