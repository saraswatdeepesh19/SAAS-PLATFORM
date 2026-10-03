package com.saas.usage.controller;

import com.saas.usage.dto.UsageSummaryResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/usage")
public class UsageController {
    private final JdbcTemplate jdbcTemplate;

    public UsageController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Returns a tenant-scoped monthly summary and zero totals when no usage has been recorded. */
    @GetMapping("/summary")
    public UsageSummaryResponse summary(@RequestParam("period") String period, @AuthenticationPrincipal Jwt jwt) {
        if (!period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "period must use YYYY-MM format");
        }
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenantId"));
        return jdbcTemplate.query(
                "SELECT total_seconds, total_sessions FROM usage_monthly WHERE tenant_id = ? AND period = ?",
                resultSet -> resultSet.next()
                        ? new UsageSummaryResponse(tenantId, period, resultSet.getLong(1),
                                resultSet.getLong(1) / 60, resultSet.getLong(2))
                        : new UsageSummaryResponse(tenantId, period, 0, 0, 0),
                tenantId, period);
    }
}