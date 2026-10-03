package com.saas.auth.controller;

import com.saas.auth.dto.CreateUserRequest;
import com.saas.auth.dto.LoginRequest;
import com.saas.auth.dto.LoginResponse;
import com.saas.auth.dto.RegisterTenantRequest;
import com.saas.auth.dto.TenantResponse;
import com.saas.auth.dto.UserResponse;
import com.saas.auth.service.AuthService;
import com.saas.common.constants.JwtClaims;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a tenant and its initial administrator, returning the created resource.
     * HTTP 201 communicates that registration created new tenant and user records.
     */
    @PostMapping("/register-tenant")
    public ResponseEntity<TenantResponse> registerTenant(@Valid @RequestBody RegisterTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerTenant(request));
    }

    /**
     * Authenticates the supplied credentials and returns the resulting access token.
     * Keeping credential checks in the service separates HTTP handling from authentication rules.
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Creates a user inside the tenant identified by the authenticated JWT.
     * Deriving tenant scope from the token prevents callers from choosing another tenant in the request body.
     */
    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID tenantId = UUID.fromString(jwt.getClaimAsString(JwtClaims.TENANT_ID));
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createUser(tenantId, request));
    }

    /**
     * Returns the authenticated user's profile within their token's tenant.
     * Using both token claims keeps the lookup scoped to the caller's own account and tenant.
     */
    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UUID tenantId = UUID.fromString(jwt.getClaimAsString(JwtClaims.TENANT_ID));
        return authService.getCurrentUser(userId, tenantId);
    }
}