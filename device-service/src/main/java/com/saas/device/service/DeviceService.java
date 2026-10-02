package com.saas.device.service;

import com.saas.device.dto.CreateDeviceRequest;
import com.saas.device.dto.DeviceResponse;
import com.saas.device.dto.UpdateDeviceRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceService {
    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);
    private final JdbcTemplate jdbcTemplate;

    public DeviceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DeviceResponse create(UUID tenantId, CreateDeviceRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO devices (id, tenant_id, name, type, os) VALUES (?, ?, ?, ?, ?)",
                id, tenantId, request.name().trim(), request.type(), request.os());
        logger.info("Device created deviceId={} tenantId={} type={}", id, tenantId, request.type());
        return get(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(UUID tenantId) {
        return jdbcTemplate.query(
                "SELECT * FROM devices WHERE tenant_id = ? AND status <> 'RETIRED' ORDER BY created_at DESC",
                this::mapDevice, tenantId);
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID tenantId, UUID deviceId) {
        return jdbcTemplate.query("SELECT * FROM devices WHERE id = ? AND tenant_id = ? AND status <> 'RETIRED'",
                        this::mapDevice, deviceId, tenantId)
                .stream().findFirst().orElseThrow(() -> notFound("Device not found"));
    }

    @Transactional
    public DeviceResponse update(UUID tenantId, UUID deviceId, UpdateDeviceRequest request) {
        int updated = jdbcTemplate.update(
                "UPDATE devices SET name = ?, type = ?, os = ?, version = version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status IN ('AVAILABLE', 'OFFLINE')",
                request.name().trim(), request.type(), request.os(), deviceId, tenantId);
        if (updated == 0) {
            ensureDeviceExists(tenantId, deviceId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device cannot be updated in its current state");
        }
        logger.info("Device updated deviceId={} tenantId={} type={}", deviceId, tenantId, request.type());
        return get(tenantId, deviceId);
    }

    @Transactional
    public void retire(UUID tenantId, UUID deviceId) {
        int updated = jdbcTemplate.update(
                "UPDATE devices SET status = 'RETIRED', version = version + 1 "
                        + "WHERE id = ? AND tenant_id = ? AND status IN ('AVAILABLE', 'OFFLINE')",
                deviceId, tenantId);
        if (updated == 0) {
            ensureDeviceExists(tenantId, deviceId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device cannot be retired while in use");
        }
        logger.info("Device retired deviceId={} tenantId={}", deviceId, tenantId);
    }

    private void ensureDeviceExists(UUID tenantId, UUID deviceId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM devices WHERE id = ? AND tenant_id = ? AND status <> 'RETIRED')",
                Boolean.class, deviceId, tenantId);
        if (!Boolean.TRUE.equals(exists)) {
            throw notFound("Device not found");
        }
    }

    private DeviceResponse mapDevice(ResultSet resultSet, int row) throws SQLException {
        return new DeviceResponse(
                resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
                resultSet.getString("name"), resultSet.getString("type"), resultSet.getString("os"),
                resultSet.getString("status"), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}