package com.saas.usage.dto;

import java.util.UUID;

/** Immutable tenant/month usage totals used by the reporting endpoint. */
public record UsageSummaryResponse(
        UUID tenantId, String period, long totalSeconds, long totalMinutes, long totalSessions) {
}