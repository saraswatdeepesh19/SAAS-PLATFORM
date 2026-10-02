package com.saas.device.controller;

import com.saas.device.dto.CreateDeviceRequest;
import com.saas.device.dto.DeviceResponse;
import com.saas.device.dto.UpdateDeviceRequest;
import com.saas.device.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(
            @Valid @RequestBody CreateDeviceRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deviceService.create(tenantId(jwt), request));
    }

    @GetMapping
    public List<DeviceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return deviceService.list(tenantId(jwt));
    }

    @GetMapping("/{deviceId}")
    public DeviceResponse get(@PathVariable("deviceId") UUID deviceId, @AuthenticationPrincipal Jwt jwt) {
        return deviceService.get(tenantId(jwt), deviceId);
    }

    @PutMapping("/{deviceId}")
    public DeviceResponse update(
            @PathVariable("deviceId") UUID deviceId,
            @Valid @RequestBody UpdateDeviceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return deviceService.update(tenantId(jwt), deviceId, request);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> retire(@PathVariable("deviceId") UUID deviceId, @AuthenticationPrincipal Jwt jwt) {
        deviceService.retire(tenantId(jwt), deviceId);
        return ResponseEntity.noContent().build();
    }

    private UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenantId"));
    }
}