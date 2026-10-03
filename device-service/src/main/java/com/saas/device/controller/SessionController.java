package com.saas.device.controller;

import com.saas.device.dto.SessionResponse;
import com.saas.device.service.SessionService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Starts a session for the current user and tenant, returning HTTP 201 for the new session. */
    @PostMapping("/devices/{deviceId}/sessions/start")
    public ResponseEntity<SessionResponse> start(
            @PathVariable("deviceId") UUID deviceId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.start(
                UUID.fromString(jwt.getClaimAsString("tenantId")), UUID.fromString(jwt.getSubject()), deviceId));
    }

    /** Ends a session only within the authenticated tenant's scope. */
    @PostMapping("/sessions/{sessionId}/end")
    public SessionResponse end(@PathVariable("sessionId") UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return sessionService.end(UUID.fromString(jwt.getClaimAsString("tenantId")), sessionId);
    }

    /** Lists session history for the authenticated tenant. */
    @GetMapping("/sessions")
    public List<SessionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return sessionService.list(UUID.fromString(jwt.getClaimAsString("tenantId")));
    }
}