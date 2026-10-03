package com.saas.billing.dto;

import java.math.BigDecimal;

/** Immutable itemized charge returned with an invoice so customers can inspect its calculation. */
public record InvoiceLineResponse(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {
}