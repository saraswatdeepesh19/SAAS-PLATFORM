package com.saas.billing.dto;

import java.math.BigDecimal;

public record InvoiceLineResponse(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
}