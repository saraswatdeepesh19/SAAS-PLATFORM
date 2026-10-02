package com.saas.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id, UUID tenantId, String invoiceNumber, String period, BigDecimal totalAmount,
        String currency, String status, Instant createdAt, List<InvoiceLineResponse> lines) {
}