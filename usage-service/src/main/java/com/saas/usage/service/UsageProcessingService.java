package com.saas.usage.service;

import com.saas.common.events.SessionEndedEvent;
import com.saas.common.events.UsageAggregatedEvent;
import com.saas.usage.config.KafkaErrorConfiguration.InvalidUsageEventException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageProcessingService {
        private static final Logger logger = LoggerFactory.getLogger(UsageProcessingService.class);
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public UsageProcessingService(JdbcTemplate jdbcTemplate, ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void process(SessionEndedEvent event) {
        validate(event);
        int inserted = jdbcTemplate.update(
                "INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT DO NOTHING", event.eventId());
        if (inserted == 0) {
                        logger.debug("Duplicate usage event skipped eventId={} tenantId={}", event.eventId(), event.tenantId());
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO usage_records (id, tenant_id, device_id, session_id, duration_sec, started_at, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), event.tenantId(), event.deviceId(), event.sessionId(), event.durationSec(),
                Timestamp.from(event.startedAt()), Timestamp.from(event.endedAt()));
        String period = PERIOD_FORMAT.format(event.endedAt());
        jdbcTemplate.update(
                "INSERT INTO usage_monthly (tenant_id, period, total_seconds, total_sessions) VALUES (?, ?, ?, 1) "
                        + "ON CONFLICT (tenant_id, period) DO UPDATE SET "
                        + "total_seconds = usage_monthly.total_seconds + EXCLUDED.total_seconds, "
                        + "total_sessions = usage_monthly.total_sessions + 1, updated_at = now()",
                event.tenantId(), period, event.durationSec());

        long[] totals = jdbcTemplate.queryForObject(
                "SELECT total_seconds, total_sessions FROM usage_monthly WHERE tenant_id = ? AND period = ?",
                (resultSet, row) -> new long[] {resultSet.getLong(1), resultSet.getLong(2)},
                event.tenantId(), period);
        eventPublisher.publishEvent(new UsageAggregatedEvent(
                UUID.randomUUID(), event.tenantId(), period, totals[0], totals[1], Instant.now()));
        logger.info("Usage session recorded eventId={} sessionId={} tenantId={} period={} totalSeconds={} totalSessions={}",
                event.eventId(), event.sessionId(), event.tenantId(), period, totals[0], totals[1]);
    }

    public void validate(SessionEndedEvent event) {
        if (event == null || event.eventId() == null || event.tenantId() == null || event.deviceId() == null
                || event.sessionId() == null || event.startedAt() == null || event.endedAt() == null
                || event.durationSec() < 0 || event.endedAt().isBefore(event.startedAt())) {
            throw new InvalidUsageEventException("SessionEnded event is missing required data or has invalid values");
        }
    }
}