package com.saas.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceGeneratedEvent(
        UUID eventId,
        UUID tenantId,
        UUID invoiceId,
        String invoiceNumber,
        String period,
        BigDecimal totalAmount,
        String currency,
        String recipientEmail,
        Instant occurredAt) {
}