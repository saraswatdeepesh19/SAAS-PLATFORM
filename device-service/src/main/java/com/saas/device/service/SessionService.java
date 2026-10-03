package com.saas.device.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.common.constants.Topics;
import com.saas.common.events.SessionEndedEvent;
import com.saas.device.dto.SessionResponse;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {
        private static final Logger logger = LoggerFactory.getLogger(SessionService.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
        private final Tracer tracer;
        private final Propagator propagator;

        public SessionService(
                        JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Tracer tracer, Propagator propagator) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
                this.tracer = tracer;
                this.propagator = propagator;
    }

        /**
         * Reserves an available device and creates its active session atomically.
         * Updating the device first prevents concurrent starts from assigning it to multiple users.
         */
        @Transactional
    public SessionResponse start(UUID tenantId, UUID userId, UUID deviceId) {
        int updated = jdbcTemplate.update(
                "UPDATE devices SET status = 'IN_USE', version = version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'AVAILABLE'",
                deviceId, tenantId);
        if (updated == 0) {
            ensureDeviceExists(tenantId, deviceId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device is not available");
        }

        UUID sessionId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO device_sessions (id, tenant_id, device_id, user_id, status, started_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)",
                sessionId, tenantId, deviceId, userId, Timestamp.from(startedAt));
        logger.info("Device session started sessionId={} deviceId={} tenantId={} userId={}",
                sessionId, deviceId, tenantId, userId);
        return new SessionResponse(sessionId, deviceId, "ACTIVE", startedAt, null, null);
    }

        /**
         * Ends an active session, releases its device, and records a usage event in the outbox.
         * One transaction keeps these state changes consistent and allows reliable asynchronous delivery.
         */
        @Transactional
    public SessionResponse end(UUID tenantId, UUID sessionId) {
        List<SessionRow> sessions = jdbcTemplate.query(
                "SELECT id, device_id, user_id, status, started_at FROM device_sessions "
                        + "WHERE id = ? AND tenant_id = ? FOR UPDATE",
                this::mapSession, sessionId, tenantId);
        SessionRow session = sessions.stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!"ACTIVE".equals(session.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session has already ended");
        }

        Instant endedAt = Instant.now();
        long durationSeconds = Math.max(0, Duration.between(session.startedAt(), endedAt).getSeconds());
        jdbcTemplate.update(
                "UPDATE device_sessions SET status = 'ENDED', ended_at = ?, duration_sec = ? WHERE id = ?",
                Timestamp.from(endedAt), durationSeconds, sessionId);
        jdbcTemplate.update(
                "UPDATE devices SET status = 'AVAILABLE', version = version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status = 'IN_USE'",
                session.deviceId(), tenantId);

        SessionEndedEvent event = new SessionEndedEvent(
                UUID.randomUUID(), tenantId, session.deviceId(), sessionId,
                durationSeconds, session.startedAt(), endedAt);
        Map<String, String> traceContext = new HashMap<>();
        if (tracer.currentSpan() != null) {
            propagator.inject(tracer.currentSpan().context(), traceContext, Map::put);
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            jdbcTemplate.update(
                    "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, topic, event_key, payload, traceparent) "
                            + "VALUES (?, 'DeviceSession', ?, ?, ?, ?::jsonb, ?)",
                    event.eventId(), sessionId, Topics.USAGE_EVENTS, tenantId.toString(), payload,
                    traceContext.get("traceparent"));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize session event", exception);
        }
        logger.info("Device session ended sessionId={} deviceId={} tenantId={} durationSeconds={} eventId={}",
                sessionId, session.deviceId(), tenantId, durationSeconds, event.eventId());
        return new SessionResponse(sessionId, session.deviceId(), "ENDED", session.startedAt(), endedAt, durationSeconds);
    }

        /** Lists tenant-scoped session history newest first for the session view. */
        @Transactional(readOnly = true)
    public List<SessionResponse> list(UUID tenantId) {
        return jdbcTemplate.query(
                "SELECT id, device_id, status, started_at, ended_at, duration_sec "
                        + "FROM device_sessions WHERE tenant_id = ? ORDER BY started_at DESC",
                (resultSet, row) -> new SessionResponse(
                        resultSet.getObject("id", UUID.class), resultSet.getObject("device_id", UUID.class),
                        resultSet.getString("status"), resultSet.getTimestamp("started_at").toInstant(),
                        resultSet.getTimestamp("ended_at") == null ? null : resultSet.getTimestamp("ended_at").toInstant(),
                        (Long) resultSet.getObject("duration_sec")),
                tenantId);
    }

        /** Converts the locked database row into the internal session state used during completion. */
        private SessionRow mapSession(ResultSet resultSet, int row) throws SQLException {
        return new SessionRow(
                resultSet.getObject("id", UUID.class), resultSet.getObject("device_id", UUID.class),
                resultSet.getObject("user_id", UUID.class), resultSet.getString("status"),
                resultSet.getTimestamp("started_at").toInstant());
    }

        /** Ensures a failed reservation was not caused by a missing or foreign-tenant device. */
        private void ensureDeviceExists(UUID tenantId, UUID deviceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM devices WHERE id = ? AND tenant_id = ? AND status <> 'RETIRED')",
                Boolean.class, deviceId, tenantId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found");
        }
    }

        /** Holds only the session fields required to validate and complete a locked session. */
        private record SessionRow(UUID id, UUID deviceId, UUID userId, String status, Instant startedAt) {
    }
}