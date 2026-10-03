package com.saas.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saas.auth.dto.RegisterTenantRequest;
import com.saas.auth.entity.TenantEntity;
import com.saas.auth.entity.UserAccount;
import com.saas.auth.exception.DuplicateResourceException;
import com.saas.auth.repository.TenantRepository;
import com.saas.auth.repository.UserAccountRepository;
import com.saas.auth.security.JwtService;
import com.saas.common.events.TenantRegisteredEvent;
import com.saas.common.enums.PlanType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserAccountRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthService authService;

    /** Builds the service with isolated mocks so each test controls its dependencies. */
    @BeforeEach
    void setUp() {
        authService = new AuthService(tenantRepository, userRepository, passwordEncoder, jwtService, eventPublisher);
    }

    /** Verifies successful registration stores an admin and publishes the matching tenant event. */
    @Test
    void registerTenantCreatesAdminAndPublishesTenantEvent() {
        when(tenantRepository.existsByNameIgnoreCase("Acme")).thenReturn(false);
        when(userRepository.existsByEmail("admin@acme.test")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(tenantRepository.save(any(TenantEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.registerTenant(
                new RegisterTenantRequest(" Acme ", "Admin@Acme.Test", "password123", null));

        assertEquals("Acme", response.tenantName());
        ArgumentCaptor<TenantRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(TenantRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(response.tenantId(), eventCaptor.getValue().tenantId());
        assertEquals(PlanType.FREE_TIER, eventCaptor.getValue().planType());
        verify(userRepository).save(any(UserAccount.class));
    }

    /** Verifies duplicate tenant names fail before any persistence or event side effect occurs. */
    @Test
    void registerTenantRejectsDuplicateTenantNameBeforeWriting() {
        when(tenantRepository.existsByNameIgnoreCase("Acme")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.registerTenant(
                new RegisterTenantRequest("Acme", "admin@acme.test", "password123", null)));

        verify(tenantRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(TenantRegisteredEvent.class));
    }
}