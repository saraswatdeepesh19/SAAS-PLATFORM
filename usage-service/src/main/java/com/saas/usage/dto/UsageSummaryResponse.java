package com.saas.usage.dto;

import java.util.UUID;

public record UsageSummaryResponse(
        UUID tenantId, String period, long totalSeconds, long totalMinutes, long totalSessions) {
}