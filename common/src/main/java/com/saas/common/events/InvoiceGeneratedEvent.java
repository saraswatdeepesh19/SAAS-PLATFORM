package com.saas.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cross-service event with invoice and recipient details for notification delivery. */
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