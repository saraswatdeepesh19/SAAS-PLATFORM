package com.saas.auth.service;

import com.saas.auth.dto.CreateUserRequest;
import com.saas.auth.dto.LoginRequest;
import com.saas.auth.dto.LoginResponse;
import com.saas.auth.dto.RegisterTenantRequest;
import com.saas.auth.dto.TenantResponse;
import com.saas.auth.dto.UserResponse;
import com.saas.auth.entity.TenantEntity;
import com.saas.auth.entity.UserAccount;
import com.saas.auth.exception.DuplicateResourceException;
import com.saas.auth.exception.InvalidCredentialsException;
import com.saas.auth.exception.ResourceNotFoundException;
import com.saas.auth.repository.TenantRepository;
import com.saas.auth.repository.UserAccountRepository;
import com.saas.auth.security.JwtService;
import com.saas.common.events.TenantRegisteredEvent;
import com.saas.common.enums.PlanType;
import com.saas.common.enums.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final TenantRepository tenantRepository;
    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(
            TenantRepository tenantRepository,
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ApplicationEventPublisher eventPublisher) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a tenant, its first administrator, and a registration event in one transaction.
     * Atomic persistence prevents a partially initialized tenant; publishing after commit avoids announcing rolled-back data.
     */
    @Transactional
    public TenantResponse registerTenant(RegisterTenantRequest request) {
        String tenantName = request.tenantName().trim();
        String adminEmail = normalizeEmail(request.adminEmail());
        if (tenantRepository.existsByNameIgnoreCase(tenantName)) {
            throw new DuplicateResourceException("Tenant name is already registered");
        }
        if (userRepository.existsByEmail(adminEmail)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Instant now = Instant.now();
        TenantEntity tenant = tenantRepository.save(new TenantEntity(
                UUID.randomUUID(), tenantName,
                request.planType() == null ? PlanType.FREE_TIER : request.planType(), now));
        UserAccount admin = userRepository.save(new UserAccount(
                UUID.randomUUID(), tenant, adminEmail, passwordEncoder.encode(request.password()), Role.ROLE_ADMIN, now));

        eventPublisher.publishEvent(new TenantRegisteredEvent(
                UUID.randomUUID(), tenant.getId(), tenant.getName(), admin.getEmail(), tenant.getPlanType(), now));
        logger.info("Tenant registered tenantId={} adminUserId={} plan={}",
            tenant.getId(), admin.getId(), tenant.getPlanType());
        return new TenantResponse(tenant.getId(), tenant.getName(), admin.getId());
    }

    /**
     * Validates a user's enabled status and password, then issues a bearer token.
     * A read-only transaction is sufficient because authentication must not mutate account data.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserAccount user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
            logger.info("User authenticated userId={} tenantId={} role={}",
                user.getId(), user.getTenant().getId(), user.getRole());
        return new LoginResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds());
    }

    /**
     * Adds a user to the specified tenant after checking email uniqueness and tenant existence.
     * The transaction keeps the lookup and insert consistent while the password is stored only as a hash.
     */
    @Transactional
    public UserResponse createUser(UUID tenantId, CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        UserAccount user = userRepository.save(new UserAccount(
                UUID.randomUUID(), tenant, email, passwordEncoder.encode(request.password()), request.role(), Instant.now()));
        logger.info("Tenant user created userId={} tenantId={} role={}", user.getId(), tenantId, user.getRole());
        return toUserResponse(user);
    }

    /**
     * Looks up a user only when the supplied tenant matches the user's tenant.
     * This check prevents a valid user ID from being used across tenant boundaries.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId, UUID tenantId) {
        UserAccount user = userRepository.findById(userId)
                .filter(account -> account.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toUserResponse(user);
    }

    /**
     * Converts the persistence entity to the public user response shape.
     * Keeping this mapping explicit prevents internal fields such as password hashes from reaching API clients.
     */
    private UserResponse toUserResponse(UserAccount user) {
        return new UserResponse(user.getId(), user.getTenant().getId(), user.getEmail(), user.getRole());
    }

    /**
     * Trims and lowercases email addresses using a locale-independent rule.
     * Normalization makes registration and login comparisons consistent regardless of input casing or server locale.
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}